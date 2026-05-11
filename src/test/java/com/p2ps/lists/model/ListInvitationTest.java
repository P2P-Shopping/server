package com.p2ps.lists.model;

import com.p2ps.auth.model.Users;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListInvitationTest {

    @Test
    void testGettersSetters() {
        ListInvitation inv = new ListInvitation();
        UUID id = UUID.randomUUID();
        inv.setId(id);

        Users inviter = new Users();
        inviter.setId(1);
        Users invitee = new Users();
        invitee.setId(2);

        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());

        inv.setShoppingList(list);
        inv.setInviter(inviter);
        inv.setInvitee(invitee);
        inv.setStatus(InvitationStatus.DECLINED);

        assertThat(inv.getId()).isEqualTo(id);
        assertThat(inv.getShoppingList()).isEqualTo(list);
        assertThat(inv.getInviter()).isEqualTo(inviter);
        assertThat(inv.getInvitee()).isEqualTo(invitee);
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    void defaultStatusShouldBePending() {
        ListInvitation inv = new ListInvitation();
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void onCreateShouldSetCreatedAt() throws Exception {
        ListInvitation inv = new ListInvitation();
        assertThat(inv.getCreatedAt()).isNull();

        Method onCreate = ListInvitation.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(inv);

        assertThat(inv.getCreatedAt()).isNotNull();
        assertThat(inv.getCreatedAt()).isBeforeOrEqualTo(java.time.LocalDateTime.now());
    }
}
