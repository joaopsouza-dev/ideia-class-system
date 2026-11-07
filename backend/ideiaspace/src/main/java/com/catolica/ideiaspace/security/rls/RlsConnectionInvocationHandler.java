package com.catolica.ideiaspace.security.rls;

import com.catolica.ideiaspace.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class RlsConnectionInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(RlsConnectionInvocationHandler.class);

    private final Connection target;
    private boolean contextSetInTransaction = false;

    public RlsConnectionInvocationHandler(Connection target) {
        this.target = target;
    }

    public static Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new RlsConnectionInvocationHandler(connection)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if ("unwrap".equals(methodName) && args != null && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            if (iface.isInstance(target)) {
                return target;
            }
        } else if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            if (iface.isInstance(target)) {
                return true;
            }
        }

        if ("prepareStatement".equals(methodName)
                || "createStatement".equals(methodName)
                || "prepareCall".equals(methodName)) {
            applyRlsContextIfNeeded();
        } else if ("commit".equals(methodName) || "rollback".equals(methodName)) {
            contextSetInTransaction = false;
        } else if ("close".equals(methodName)) {
            contextSetInTransaction = false;
        }

        return method.invoke(target, args);
    }

    private void applyRlsContextIfNeeded() {
        if (contextSetInTransaction) {
            return;
        }

        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }

        try {
            boolean inTransaction = !target.getAutoCommit();
            try (PreparedStatement ps = target.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
                ps.setString(1, currentUserId.toString());
                ps.execute();
            }
            if (inTransaction) {
                contextSetInTransaction = true;
            }
            log.debug("RLS context app.current_user_id={} established (inTransaction: {})", currentUserId, inTransaction);
        } catch (Exception e) {
            log.error("Failed to establish PostgreSQL RLS context for user {}", currentUserId, e);
            throw new RuntimeException("Falha ao estabelecer contexto RLS no PostgreSQL", e);
        }
    }
}
