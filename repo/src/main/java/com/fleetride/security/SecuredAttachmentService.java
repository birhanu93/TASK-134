package com.fleetride.security;

import com.fleetride.domain.Attachment;
import com.fleetride.domain.Order;
import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.repository.OrderRepository;
import com.fleetride.service.AttachmentService;
import com.fleetride.service.ShareLinkService;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public final class SecuredAttachmentService {
    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String msg) { super(msg); }
    }

    private final AttachmentService delegate;
    private final Authorizer authz;
    private final ShareLinkService shareLinks;
    private final OrderRepository orders;

    public SecuredAttachmentService(AttachmentService delegate, Authorizer authz,
                                    ShareLinkService shareLinks, OrderRepository orders) {
        this.delegate = delegate;
        this.authz = authz;
        this.shareLinks = shareLinks;
        this.orders = orders;
    }

    public Attachment upload(String orderId, String filename, String mimeType,
                             InputStream data, long sizeBytes) {
        User u = authz.require(Permission.ATTACHMENT_UPLOAD);
        requireOrderAccess(u, orderId);
        return delegate.upload(orderId, filename, mimeType, data, sizeBytes);
    }

    public Optional<Attachment> find(String id) {
        User u = authz.require(Permission.ATTACHMENT_READ);
        Optional<Attachment> a = delegate.find(id);
        if (a.isEmpty()) return a;
        if (!hasOrderAccess(u, a.get().orderId())) return Optional.empty();
        return a;
    }

    public List<Attachment> listForOrder(String orderId) {
        User u = authz.require(Permission.ATTACHMENT_READ);
        if (!hasOrderAccess(u, orderId)) return List.of();
        return delegate.listForOrder(orderId);
    }

    public void delete(String id) {
        User u = authz.require(Permission.ATTACHMENT_DELETE);
        Attachment a = delegate.find(id)
                .orElseThrow(() -> new AccessDeniedException("unknown attachment"));
        requireOrderAccess(u, a.orderId());
        delegate.delete(id);
    }

    public String issueShareToken(String attachmentId, int ttlHours) {
        User u = authz.require(Permission.SHARE_LINK_CREATE);
        Attachment a = delegate.find(attachmentId)
                .orElseThrow(() -> new AccessDeniedException("unknown attachment"));
        requireOrderAccess(u, a.orderId());
        return shareLinks.create("attachment:" + attachmentId, ttlHours).token();
    }

    public Attachment resolveByToken(String token) {
        String resource = shareLinks.resolve(token);
        if (!resource.startsWith("attachment:")) {
            throw new AccessDeniedException("token not scoped to an attachment");
        }
        String attachmentId = resource.substring("attachment:".length());
        return delegate.find(attachmentId)
                .orElseThrow(() -> new AccessDeniedException("attachment no longer exists"));
    }

    private boolean hasOrderAccess(User u, String orderId) {
        if (u.role() == Role.ADMINISTRATOR) return true;
        if (authz.canSeeAll(u.role())) return true;
        Optional<Order> o = orders.findById(orderId);
        if (o.isEmpty()) return false;
        String owner = o.get().ownerUserId();
        return owner != null && owner.equals(u.id());
    }

    private void requireOrderAccess(User u, String orderId) {
        if (!hasOrderAccess(u, orderId)) {
            throw new AccessDeniedException("order owned by another user");
        }
    }
}
