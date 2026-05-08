CREATE TABLE IF NOT EXISTS list_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shopping_list_id UUID NOT NULL,
    inviter_id INTEGER NOT NULL,
    invitee_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitation_list FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (inviter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_invitee FOREIGN KEY (invitee_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_invitation_list_invitee_pending ON list_invitations(shopping_list_id, invitee_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_invitations_invitee ON list_invitations(invitee_id);
CREATE INDEX IF NOT EXISTS idx_invitations_list ON list_invitations(shopping_list_id);
