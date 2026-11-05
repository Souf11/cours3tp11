# Cours 3 - TP 1.1 : Authentification et Autorisation Spring Security avec Spring Data JPA & MySQL

## Étape 1 – Création du projet Spring Boot
Utiliser [https://start.spring.io](https://start.spring.io) :
- **Project** : Maven
- **Group** : `ma.fstg.security`
- **Artifact** : `spring-security-jpa`
- **Dependencies** :
  - Spring Web
  - Spring Security
  - Spring Data JPA
  - MySQL Driver
  - Thymeleaf
  - Lombok
  - DevTools

Télécharger et ouvrir le projet dans IntelliJ ou Eclipse.

---

## Étape 2 – Configuration de la base MySQL
Crée une base de données dans MySQL :
```sql
CREATE DATABASE security_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Dans `src/main/resources/application.properties` :
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/security_db?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

### Explication :
- `ddl-auto=update` crée ou met à jour automatiquement les tables à partir des entités JPA.
- `show-sql=true` affiche les requêtes SQL dans la console (utile pour le suivi).

---

## Étape 3 – Modélisation des entités

### Entité `Role.java`
**Chemin :** `src/main/java/ma/fstg/security/entities/Role.java`
```java
package ma.fstg.security.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}
```

### Entité `User.java`
**Chemin :** `src/main/java/ma/fstg/security/entities/User.java`
```java
package ma.fstg.security.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private boolean active;

    @ManyToMany(fetch = FetchType.EAGER)
    private Collection<Role> roles = new ArrayList<>();
}
```

### Explication :
- La relation `@ManyToMany` permet à un utilisateur d’avoir plusieurs rôles (USER, ADMIN, etc.).
- L’attribut `fetch = FetchType.EAGER` charge automatiquement les rôles avec l’utilisateur (nécessaire pour la sécurité).
- Le champ `active` permettra de désactiver un compte sans le supprimer.

---

## Étape 4 – Création des repositories

### `UserRepository.java`
**Chemin :** `src/main/java/ma/fstg/security/repositories/UserRepository.java`
```java
package ma.fstg.security.repositories;

import ma.fstg.security.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
```

### `RoleRepository.java`
**Chemin :** `src/main/java/ma/fstg/security/repositories/RoleRepository.java`
```java
package ma.fstg.security.repositories;

import ma.fstg.security.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Role findByName(String name);
}
```

### Explication :
- Ces interfaces héritent de `JpaRepository`, qui fournit déjà des méthodes comme `findAll()`, `save()`, ou `deleteById()`.
- Elles permettent un accès simple et typé aux entités sans écrire de requêtes SQL manuelles.

---

## Étape 5 – Service de chargement utilisateur
Crée un service qui informe Spring Security de la manière dont les utilisateurs doivent être chargés depuis la base.

### `CustomUserDetailsService.java`
**Chemin :** `src/main/java/ma/fstg/security/services/CustomUserDetailsService.java`
```java
package ma.fstg.security.services;

import ma.fstg.security.entities.User;
import ma.fstg.security.repositories.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isActive(),
                true, true, true,
                user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority(role.getName()))
                    .collect(Collectors.toList())
        );
    }
}
```

### Explication pédagogique :
- `UserDetailsService` est l’interface que Spring Security utilise pour retrouver un utilisateur par son nom.
- Le service récupère le `User` depuis la base, vérifie qu’il existe, et le transforme en objet `UserDetails` compris par Spring.
- Les rôles sont convertis en objets `GrantedAuthority`, que Spring utilise pour gérer les autorisations.

---

## Étape 6 – Configuration de la sécurité

### `SecurityConfig.java`
**Chemin :** `src/main/java/ma/fstg/security/config/SecurityConfig.java`
```java
package ma.fstg.security.config;

import ma.fstg.security.services.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/home", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            );
        return http.build();
    }
}
```

### Explication :
- `DaoAuthenticationProvider` est le pont entre Spring Security et le `UserDetailsService`.
- `BCryptPasswordEncoder` protège les mots de passe.
- `requestMatchers()` définit quelles routes sont protégées selon les rôles.
- `formLogin()` configure les redirections après login.

---

## Étape 7 – Initialisation automatique des données
Crée `DatabaseInitializer.java` :
**Chemin :** `src/main/java/ma/fstg/security/config/DatabaseInitializer.java`
```java
package ma.fstg.security.config;

import ma.fstg.security.entities.Role;
import ma.fstg.security.entities.User;
import ma.fstg.security.repositories.RoleRepository;
import ma.fstg.security.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DatabaseInitializer {

    @Bean
    CommandLineRunner init(RoleRepository roleRepo, UserRepository userRepo, BCryptPasswordEncoder encoder) {
        return args -> {
            if (roleRepo.count() == 0 && userRepo.count() == 0) {
                Role adminRole = roleRepo.save(new Role(null, "ROLE_ADMIN"));
                Role userRole = roleRepo.save(new Role(null, "ROLE_USER"));

                User admin = new User(null, "admin", encoder.encode("1234"), true, List.of(adminRole, userRole));
                User user = new User(null, "user", encoder.encode("1111"), true, List.of(userRole));

                userRepo.saveAll(List.of(admin, user));
            }
        };
    }
}
```

### Explication :
- Cette classe insère des utilisateurs et rôles par défaut au démarrage.
- Les mots de passe sont encodés avec BCrypt pour éviter le stockage en clair.

---

## Étape 8 – Test du flux complet
1. Lance le projet :
   ```bash
   mvn spring-boot:run
   ```
2. Ouvre ton navigateur à : [http://localhost:8080/login](http://localhost:8080/login)
3. Teste les identifiants :
   - `admin` / `1234` → accès à `/admin/dashboard`
   - `user` / `1111` → accès à `/user/dashboard`
4. Vérifie dans MySQL :
   - Les tables `user`, `role`, et `user_roles` ont été créées.
   - Les mots de passe sont bien encodés (chaîne longue avec `$2a$`).

