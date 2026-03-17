package org.vaadin.example.security;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.vaadin.example.views.login.LoginView;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration extends VaadinWebSecurity {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // Configurar las rutas públicas para las APIs de bots
        http.authorizeHttpRequests(authorize -> authorize
                // Imágenes estáticas
                .requestMatchers(new AntPathRequestMatcher("/images/*.png")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/favicon.ico")).permitAll()

                // APIs públicas para WhatsApp (mantener compatibilidad)
                .requestMatchers(new AntPathRequestMatcher("/api/whatsapp/**")).permitAll()

                // APIs públicas para Bot API (nuevas rutas)
                .requestMatchers(new AntPathRequestMatcher("/api/bot/**")).permitAll()

                // APIs de autenticación Telegram
                .requestMatchers(
                        new AntPathRequestMatcher("/api/bot/telegram/iniciar-activacion", HttpMethod.POST.name()),
                        new AntPathRequestMatcher("/api/bot/telegram/completar-activacion", HttpMethod.POST.name()),
                        new AntPathRequestMatcher("/api/bot/telegram/mensaje", HttpMethod.POST.name()),
                        new AntPathRequestMatcher("/api/bot/telegram/estado/**", HttpMethod.GET.name())
                ).permitAll()

                // APIs de WhatsApp (para mantener compatibilidad)
                .requestMatchers(
                        new AntPathRequestMatcher("/api/bot/whatsapp/mensaje", HttpMethod.POST.name()),
                        new AntPathRequestMatcher("/api/bot/descargar/**", HttpMethod.GET.name()),
                        new AntPathRequestMatcher("/api/bot/ver/**", HttpMethod.GET.name())
                ).permitAll()

                // APIs de debug/testing
                .requestMatchers(
                        new AntPathRequestMatcher("/api/bot/debug/**", HttpMethod.GET.name()),
                        new AntPathRequestMatcher("/api/bot/debug/**", HttpMethod.POST.name())
                ).permitAll()

                // Actuator para monitoreo (opcional)
                .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/actuator/info")).permitAll()

                // Consola H2 si estás en desarrollo
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).permitAll()
        );

        // Permitir iconos de line-awesome
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(new AntPathRequestMatcher("/line-awesome/**/*.svg")).permitAll());

        // Configurar CSRF - IMPORTANTE: Deshabilitar solo para APIs REST
        http.csrf(csrf -> csrf
                // Deshabilitar CSRF para APIs REST (necesario para webhooks)
                .ignoringRequestMatchers(
                        new AntPathRequestMatcher("/api/**"),
                        new AntPathRequestMatcher("/h2-console/**")
                )
        );

        // Configurar headers para H2 Console en desarrollo
        http.headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
        );

        super.configure(http);
        setLoginView(http, LoginView.class);

        // Log de configuración
        System.out.println("🔐 Configuración de seguridad cargada:");
        System.out.println("   ✅ APIs públicas configuradas para WhatsApp y Telegram");
        System.out.println("   ✅ CSRF deshabilitado para rutas /api/**");
        System.out.println("   ✅ H2 Console permitida (solo desarrollo)");
    }
}