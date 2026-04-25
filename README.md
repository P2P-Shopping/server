# P2P-Shopping

Please check [CONTRIBUTING.md](/docs/CONTRIBUTING.md) for guidelines. <!--, and [HELP.md](/docs/HELP.md) for help and reference documentation. -->

## Local development

You may create a local properties file at `src/main/resources/application-local.properties`, which overrides matching keys from `application.properties`.
To use it, run with the `local` Spring profile:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The local profile uses `spring.jpa.hibernate.ddl-auto=update` so Hibernate can create or adjust the schema during development.
It also sets `app.security.cookie-secure-flag=false` to allow cookies over HTTP.

If you are not using the `local` profile, you can manually disable the secure flag for cookies by exporting the `COOKIE_SECURE_FLAG` environment variable:

```bash
export COOKIE_SECURE_FLAG=false
```

In IntelliJ IDEA, you can set this up by following these steps:

- Click the configurations dropdown
- Click **Edit Configurations...**
- Select the only configuration under **Spring Boot**
- In the right pane, type *local* in the **Active profiles** box.
