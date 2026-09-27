package com.example.coffee_hrm.common.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VietnamTimeTest {

    @Test
    void usesHoChiMinhZone() {
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), VietnamTime.ZONE);
        assertEquals(LocalDate.now(VietnamTime.ZONE), VietnamTime.today());
    }
}
