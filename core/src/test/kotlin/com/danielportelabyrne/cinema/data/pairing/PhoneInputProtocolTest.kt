package com.danielportelabyrne.cinema.data.pairing

import com.danielportelabyrne.cinema.data.pairing.PhoneInputProtocol.FromPhone
import org.junit.Assert.*
import org.junit.Test

class PhoneInputProtocolTest {
    @Test fun a_text_message_carries_the_field_and_the_full_text() {
        assertEquals(FromPhone.Text("search", "heist films"), PhoneInputProtocol.parseFromPhone("Tsearch\theist films"))
    }

    @Test fun text_may_be_empty_so_clearing_the_box_is_sent() {
        assertEquals(FromPhone.Text("search", ""), PhoneInputProtocol.parseFromPhone("Tsearch\t"))
    }

    @Test fun text_keeps_any_tab_after_the_first() {
        assertEquals(FromPhone.Text("a", "b\tc"), PhoneInputProtocol.parseFromPhone("Ta\tb\tc"))
    }

    @Test fun a_text_message_without_a_field_or_tab_is_refused() {
        assertNull(PhoneInputProtocol.parseFromPhone("T"))
        assertNull(PhoneInputProtocol.parseFromPhone("Tsearch"))
        assertNull(PhoneInputProtocol.parseFromPhone("T\thello"))
    }

    @Test fun done_and_keep_alive_are_recognised() {
        assertSame(FromPhone.Done, PhoneInputProtocol.parseFromPhone("D"))
        assertSame(FromPhone.KeepAlive, PhoneInputProtocol.parseFromPhone("P"))
    }

    @Test fun anything_else_is_not_a_message() {
        assertNull(PhoneInputProtocol.parseFromPhone(""))
        assertNull(PhoneInputProtocol.parseFromPhone("X"))
        assertNull(PhoneInputProtocol.parseFromPhone("DD"))
        assertNull(PhoneInputProtocol.parseFromPhone("hello"))
    }

    @Test fun sanitise_turns_tab_and_line_breaks_into_spaces() {
        assertEquals("a b c d", PhoneInputProtocol.sanitise("a\tb\nc\rd"))
        assertEquals("plain", PhoneInputProtocol.sanitise("plain"))
    }

    @Test fun clamp_cuts_to_the_limit_and_zero_means_no_limit() {
        assertEquals("abc", PhoneInputProtocol.clampToMax("abcdef", 3))
        assertEquals("abcdef", PhoneInputProtocol.clampToMax("abcdef", 0))
        assertEquals("ab", PhoneInputProtocol.clampToMax("ab", 10))
    }

    @Test fun a_focus_message_lists_the_field_label_secret_limit_and_text() {
        assertEquals("Fsearch\tSearch\t0\t60\theist", PhoneInputProtocol.formatFocus("search", "Search", false, 60, "heist"))
    }

    @Test fun a_secret_field_never_sends_its_text() {
        val message = PhoneInputProtocol.formatFocus("password", "Password", true, 200, "hunter2")
        assertEquals("Fpassword\tPassword\t1\t200\t", message)
        assertFalse(message.contains("hunter2"))
    }

    @Test fun focus_values_cannot_break_the_framing() {
        val message = PhoneInputProtocol.formatFocus("a\tb", "La\nbel", false, 10, "te\txt")
        assertEquals(5, message.split('\t').size)
        assertFalse(message.contains('\n'))
    }

    @Test fun none_is_a_single_letter() {
        assertEquals("N", PhoneInputProtocol.formatNone())
    }
}
