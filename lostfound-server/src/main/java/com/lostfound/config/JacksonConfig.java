package com.lostfound.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 前后端时间格式兼容。
 *
 * <p>Web 发布页用 dayjs 输出 {@code yyyy-MM-dd HH:mm:ss}，小程序发布页用「日期 + 00:00:00」，
 * 而 Jackson 的 LocalDateTime 默认只接受 ISO_LOCAL_DATE_TIME（带 T）。
 * 请求体会在进入 Controller 前抛 HttpMessageNotReadableException，被兜底处理器当成 500 返回。
 * {@code spring.jackson.date-format} 只作用于 java.util.Date，对 LocalDateTime 无效，
 * 因此这里显式注册一个兼容多种写法的反序列化器。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder.deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
    }

    static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter[] FORMATS = {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        };

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) return null;
            text = text.trim();
            for (DateTimeFormatter format : FORMATS) {
                try {
                    return LocalDateTime.parse(text, format);
                } catch (DateTimeParseException ignored) {
                    // 继续尝试下一种格式
                }
            }
            try {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 交给下方统一报错
            }
            throw ctx.weirdStringException(text, LocalDateTime.class,
                    "时间格式应为 yyyy-MM-dd HH:mm:ss 或 ISO 8601（yyyy-MM-ddTHH:mm:ss）");
        }
    }
}
