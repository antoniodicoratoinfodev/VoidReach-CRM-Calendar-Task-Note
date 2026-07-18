package com.crm.repository;

/** Identity stamped into a portable export so an import can tell whose account the data belongs to. */
public record ExportOwner(String email, String name) {
}
