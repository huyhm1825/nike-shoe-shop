# ShoeHouse Pro (Nike Shoe Shop)

A premium sneaker e-commerce web app built with Spring Boot MVC, Thymeleaf, Spring Security and JPA.

## Highlights
- Nike-inspired UI with dark premium styling
- Home page, catalog, product detail
- Search, filter by brand/category/size, sort, and pagination
- Session cart
- Wishlist
- Coupon codes
- Checkout and order history
- Admin dashboard with product/category/brand/user/order management
- Sample data seeded automatically

## Demo accounts
- `admin@gmail.com` / `123456`
- `user@gmail.com` / `123456`

## Environment requirements
- Java 17
- Maven 3.9+

## Build & test
```bash
mvn clean test
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Default database (ready to run)
- Default profile uses in-memory H2 (no extra setup needed).
- H2 console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:shoeshop`
  - User: `sa`
  - Password: *(empty)*

## MySQL profile
If you want to use MySQL instead of H2:
1. Create database `shoeshop`
2. Update credentials in `src/main/resources/application-mysql.properties`
3. Run:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=mysql
   ```

## Notes / current limits
- Payment flow is demo-only (COD/Bank transfer instruction + QR preview), no real payment gateway integration.
- Seed data is created automatically when tables are empty.
