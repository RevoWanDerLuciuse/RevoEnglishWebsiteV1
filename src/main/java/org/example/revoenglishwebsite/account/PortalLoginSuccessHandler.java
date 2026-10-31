package org.example.revoenglishwebsite.account;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;

/**
 * After sign-in: back to the private page that was requested — if the user's role may open it —
 * otherwise to the user's home (administrators → /admin/students, students → /dashboard).
 * A pending password change is enforced by {@link PortalAccessInterceptor}.
 */
public class PortalLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public PortalLoginSuccessHandler() {
        setRequestCache(requestCache);
    }

    public static String homeOf(Role role) {
        return role == Role.ADMIN ? "/admin/students" : "/dashboard";
    }

    /** Whether a saved page belongs to the area this role may open (avoids sending an admin to a 403 page). */
    static boolean mayOpen(Role role, String path) {
        String area = role == Role.ADMIN ? "/admin" : "/dashboard";
        return path.equals(area) || path.startsWith(area + "/") || path.startsWith("/account/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && authentication.getPrincipal() instanceof AccountPrincipal principal
                && !mayOpen(principal.role(), URI.create(saved.getRedirectUrl()).getPath())) {
            requestCache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** Computed per request (the handler is shared, so no mutable default target). */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return homeOf(principal.role());
        }
        return "/";
    }
}
