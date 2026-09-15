package com.example.attendance.config;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.context.annotation.*;
@Configuration public class MybatisConfig { @Bean MybatisPlusInterceptor interceptor(){ MybatisPlusInterceptor i=new MybatisPlusInterceptor(); i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); return i; } }

