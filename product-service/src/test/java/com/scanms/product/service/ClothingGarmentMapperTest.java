package com.scanms.product.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClothingGarmentMapperTest {
    @Test
    void mapsTopsBottomsAndUnknown() {
        assertEquals(ClothingGarmentMapper.GarmentType.TOP, ClothingGarmentMapper.garmentType("short_sleeve_top"));
        assertEquals(ClothingGarmentMapper.GarmentType.BOTTOM, ClothingGarmentMapper.garmentType("trousers"));
        assertEquals(ClothingGarmentMapper.GarmentType.BOTTOM, ClothingGarmentMapper.garmentType("shorts"));
        assertEquals(ClothingGarmentMapper.GarmentType.UNKNOWN, ClothingGarmentMapper.garmentType("laptop"));
        assertEquals("Áo tay ngắn", ClothingGarmentMapper.displayLabel("short_sleeve_top"));
        assertTrue(ClothingGarmentMapper.isClothing("long_sleeve_dress"));
        assertFalse(ClothingGarmentMapper.isClothing("phone"));
    }
}
