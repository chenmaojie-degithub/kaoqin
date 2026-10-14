import { createHash, createPrivateKey, randomUUID, sign } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Only integer versions and string/null payload values are allowed by protocol v2.
export function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]))
  return value
}
function nameUuid(name) {
  const bytes = createHash('md5').update(name, 'utf8').digest()
  bytes[6] = (bytes[6] & 15) | 48
  bytes[8] = (bytes[8] & 63) | 128
  const hex = bytes.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export const catalogUuid = (schoolId, type, code) => nameUuid(`${schoolId}\n${type}\n${code}`)
export const studentUuid = (schoolId, studentNo) => nameUuid(`${schoolId}\n${studentNo}`)
export const recordUuid = (taskUuid, schoolId, studentNo) => nameUuid(`${taskUuid}\n${studentUuid(schoolId, studentNo)}`)
export function signEvent(event, privateKeyBase64) {
  const { signature: ignored, ...unsigned } = event
  const key = createPrivateKey({ key: Buffer.from(privateKeyBase64, 'base64'), format: 'der', type: 'pkcs8' })
  return { ...unsigned, signature: sign(null, Buffer.from(JSON.stringify(canonical(unsigned)), 'utf8'), key).toString('base64') }
}
async function probe(configFile, peerId) {
  const config = JSON.parse(await readFile(configFile, 'utf8'))
  if (!/^jdbc:mysql:\/\/127\.0\.0\.1:(?!3306\/)[0-9]+\/attendance_sync_test_[a-z0-9_]+\?/.test(config.DB_URL)) throw Error('Probe is restricted to the fictional local lab')
  const peer = JSON.parse(config.SYNC_PEERS_JSON).find(item => item.nodeId === peerId)
  if (!peer || !peer.url.startsWith('http://127.0.0.1:')) throw Error('Loopback lab peer required')
  const event = signEvent({ eventId: randomUUID(), originNodeId: config.SYNC_NODE_ID, entityType: 'STUDENT', entityId: studentUuid(config.SYNC_SCHOOL_ID, 'S001'), operation: 'UPSERT', version: 1, occurredAt: new Date().toISOString(), payload: { schoolId: config.SYNC_SCHOOL_ID, studentNo: 'S001', name: 'Fictional Student', classCode: 'CLASS_1' } }, config.SYNC_PRIVATE_KEY)
  const response = await fetch(`${peer.url}/sync/push`, { method: 'POST', signal: AbortSignal.timeout(5000), headers: { 'Content-Type': 'application/json', 'X-Sync-Node': config.SYNC_NODE_ID, Authorization: `Sync ${config.SYNC_TOKEN}` }, body: JSON.stringify({ protocolVersion: 2, nodeId: config.SYNC_NODE_ID, events: [event] }) })
  const body = await response.json()
  if (!response.ok || body.code !== 0 || !body.data?.results?.[0]?.persisted || !['APPLIED', 'DUPLICATE'].includes(body.data.results[0].status)) throw Error('Protocol interoperability probe failed')
  console.log(JSON.stringify({ probe: 'Node.js -> Java Ed25519 / canonical JSON / student UUID', ...body.data.results[0] }))
  // Observe the receiver's baseline before submitting a common record correction; never guess a version.
  const pulled = await fetch(`${peer.url}/sync/pull?cursor=0&limit=100`, { signal: AbortSignal.timeout(5000), headers: { 'X-Sync-Node': config.SYNC_NODE_ID, Authorization: `Sync ${config.SYNC_TOKEN}` } })
  const page = await pulled.json()
  if (!pulled.ok || page.code !== 0 || page.data.hasMore) throw Error('Record probe requires a small initialized fictional lab')
  const records = new Map()
  for (const { event: item } of page.data.events) if (item.entityType === 'ATTENDANCE_RECORD' && item.payload.studentNo === 'S001') {
    if (!records.has(item.entityId) || records.get(item.entityId).version < item.version) records.set(item.entityId, item)
  }
  const baseline = [...records.values()][0]
  if (!baseline) throw Error('Run Test-RunningNodes.ps1 before the common record probe')
  const correction = signEvent({ eventId: randomUUID(), originNodeId: config.SYNC_NODE_ID, entityType: 'ATTENDANCE_RECORD', entityId: baseline.entityId, operation: 'UPSERT', version: baseline.version + 1, occurredAt: new Date().toISOString(), payload: { ...baseline.payload, baseEventId: baseline.eventId, recordedAt: new Date().toISOString(), remark: '跨语言普通考勤探针' } }, config.SYNC_PRIVATE_KEY)
  const updated = await fetch(`${peer.url}/sync/push`, { method: 'POST', signal: AbortSignal.timeout(5000), headers: { 'Content-Type': 'application/json', 'X-Sync-Node': config.SYNC_NODE_ID, Authorization: `Sync ${config.SYNC_TOKEN}` }, body: JSON.stringify({ protocolVersion: 2, nodeId: config.SYNC_NODE_ID, events: [correction] }) })
  const result = await updated.json()
  if (!updated.ok || result.code !== 0 || result.data?.results?.[0]?.status !== 'APPLIED') throw Error('Common record baseline/signature probe failed')
  console.log(JSON.stringify({ probe: 'Node.js -> Java ordinary attendance update; Unicode / null fields / observed baseline', ...result.data.results[0] }))
}
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  if (process.argv[2] !== '--probe' || !process.argv[3] || !process.argv[4]) throw Error('Usage: node protocol-example.mjs --probe <node-a.local.json> NODE_B')
  await probe(process.argv[3], process.argv[4])
}
