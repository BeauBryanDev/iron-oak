package com.ironoak.domain.enums;

/** order_item.item_type (VARCHAR with a CHECK since V13; the Postgres enum was dropped). */
public enum OrderItemType {
    PRODUCT,
    SERVICE,
    MACHINE,
    /** An end mill (milling cutter), V13: sold like a product, no warranty. */
    END_MILL
}
