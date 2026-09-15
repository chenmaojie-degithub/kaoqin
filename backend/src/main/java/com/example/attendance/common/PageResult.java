package com.example.attendance.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(List<T> records, long page, long size, long total, long pages) {
    public static <T> PageResult<T> of(IPage<T> page) { return new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()); }
}

