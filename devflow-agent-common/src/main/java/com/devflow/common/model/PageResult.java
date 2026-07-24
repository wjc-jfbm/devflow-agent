package com.devflow.common.model;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应
 *
 * @param <T> 数据项类型
 */
public class PageResult<T> {

    /** 当前页码（从 1 开始） */
    private long page;
    /** 每页条数 */
    private long size;
    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 数据列表 */
    private List<T> records;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(long page, long size, long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
        this.records = records != null ? records : Collections.emptyList();
    }

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(1, 10, 0, Collections.emptyList());
    }

    // ---- getters & setters ----

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public long getPages() { return pages; }
    public void setPages(long pages) { this.pages = pages; }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
}
