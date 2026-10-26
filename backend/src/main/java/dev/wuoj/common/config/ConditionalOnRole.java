package dev.wuoj.common.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Registers the bean only when {@code oj.roles} includes the given role. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnRole.OnRoleCondition.class)
public @interface ConditionalOnRole {

    Role value();

    final class OnRoleCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnRole.class.getName());
            if (attributes == null) return false;
            Role required = (Role) attributes.get("value");
            List<Role> roles = Binder.get(context.getEnvironment())
                    .bind("oj.roles", Bindable.listOf(Role.class))
                    .orElse(List.of(Role.API, Role.WORKER));
            return roles.contains(required);
        }
    }
}
