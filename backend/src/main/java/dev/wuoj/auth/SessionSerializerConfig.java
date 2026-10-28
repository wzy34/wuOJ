package dev.wuoj.auth;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Session attributes are Java-serialized, so a session written by an older version (a changed principal class)
 * cannot be read back. Such attributes are dropped instead of failing every request with that cookie: the session
 * then has no signed-in user, which is answered like any signed-out request.
 */
@Configuration(proxyBeanMethods = false)
public class SessionSerializerConfig {

    /** The name Spring Session looks up for the serializer of session attributes. */
    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new TolerantSerializer();
    }

    static final class TolerantSerializer implements RedisSerializer<Object>, BeanClassLoaderAware {

        private static final Logger log = LoggerFactory.getLogger(SessionSerializerConfig.class);

        private RedisSerializer<Object> delegate = new JdkSerializationRedisSerializer();

        @Override
        public void setBeanClassLoader(ClassLoader classLoader) {
            delegate = new JdkSerializationRedisSerializer(classLoader);
        }

        @Override
        public byte @Nullable [] serialize(@Nullable Object value) {
            return delegate.serialize(value);
        }

        @Override
        public @Nullable Object deserialize(byte @Nullable [] bytes) {
            try {
                return delegate.deserialize(bytes);
            } catch (SerializationException e) {
                log.debug("Ignoring a session attribute that cannot be read", e);
                return null;
            }
        }
    }
}
