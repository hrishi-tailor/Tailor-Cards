# Tailor Cards E-Commerce

A full-stack e-commerce platform designed for trading cards, card accessories, and graded collectibles. Built with a **Spring Boot** and **Java 21** REST backend backed by **PostgreSQL**, paired with a **React 19**, **TypeScript**, and **Vite** frontend.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot, Spring Data JPA, Hibernate, Spring Security, Lombok |
| **Frontend** | React 19, TypeScript, Vite, React Router v7, Context API |
| **Database** | PostgreSQL (compatible with local PostgreSQL, Docker, and Supabase) |
| **Build & Tooling** | Maven (`./mvnw`), npm, Vite Dev Server Proxy |

---

## Implemented Features

### 1. Relational Database Schema
* **Categories & Products**: Configured with a `@ManyToOne` relationship ([`Product.java`](src/main/java/com/tailorcards/api/entity/Product.java) $\rightarrow$ [`Category.java`](src/main/java/com/tailorcards/api/entity/Category.java)) featuring lazy loading (`FetchType.LAZY`), foreign key constraints, and cascading rules.
* **Guest Cart Items**: [`CartItem.java`](src/main/java/com/tailorcards/api/entity/CartItem.java) links to `Product` and groups items by an indexed `cartSessionId`.
* **Idempotent Seed Data**: Pre-populated with sample categories and products via [`data.sql`](src/main/resources/data.sql) using `ON CONFLICT` and `NOT EXISTS` checks.

### 2. Layered Architecture & DTO Pattern
* **Decoupled Architecture**: Strict separation across Controller $\rightarrow$ Service $\rightarrow$ Repository layers.
* **Java 21 Records as DTOs**: Request and response payloads use immutable Java records ([`ProductResponse`](src/main/java/com/tailorcards/api/dto/ProductResponse.java), [`ProductRequest`](src/main/java/com/tailorcards/api/dto/ProductRequest.java), [`CartResponse`](src/main/java/com/tailorcards/api/dto/CartResponse.java), etc.) with Jakarta Bean Validation (`@NotBlank`, `@Min`, `@DecimalMin`).
* **Component Mappers**: Dedicated Spring `@Component` mappers ([`ProductMapper`](src/main/java/com/tailorcards/api/mapper/ProductMapper.java), [`CategoryMapper`](src/main/java/com/tailorcards/api/mapper/CategoryMapper.java)) isolate persistence models from REST API contracts, preventing circular JSON serialization and `LazyInitializationException`.

### 3. Global Exception Handling
* Centralized [`GlobalExceptionHandler`](src/main/java/com/tailorcards/api/exception/GlobalExceptionHandler.java) using `@RestControllerAdvice`.
* Emits uniform [`ErrorResponse`](src/main/java/com/tailorcards/api/exception/ErrorResponse.java) JSON objects with timestamps, HTTP status codes, error messages, and request URIs.
* Handles `ResourceNotFoundException` (404), `MethodArgumentNotValidException` (400), and `IllegalArgumentException` (400 for stock limit violations).

### 4. Spring Security Configuration
* Configured in [`SecurityConfig.java`](src/main/java/com/tailorcards/api/config/SecurityConfig.java) with `SecurityFilterChain`:
  * **Public Access (`permitAll`)**: `GET /api/**` and all cart operations (`/api/cart/**`).
  * **Protected Operations**: `POST`, `PUT`, `DELETE` operations on `/api/products` and `/api/categories` require HTTP Basic authentication.
  * **Stateless REST**: Stateless session creation policy (`SessionCreationPolicy.STATELESS`) and CSRF protection safely disabled for stateless API operations.

### 5. Guest Shopping Cart
* **Client Session Tracking**: Anonymous visitors receive a UUID generated via `crypto.randomUUID()` stored in `localStorage` (`tc_cart_session_id`), allowing frictionless shopping without requiring user login.
* **Stock Validation & Quantity Merging**: Adding existing items increments their quantity while validating that the requested total does not exceed available inventory.
* **Full Cart Operations**: Fetch cart summary (`GET /api/cart/{cartSessionId}`), add items (`POST /api/cart/{cartSessionId}`), and remove line items (`DELETE /api/cart/{cartSessionId}/items/{itemId}`).

### 6. React Frontend Catalog & UI
* **Vite API Reverse Proxy**: [`vite.config.ts`](frontend/vite.config.ts) proxies `/api/*` calls to `http://localhost:8080`, completely eliminating cross-origin (CORS) preflight issues during development.
* **Global Cart Context**: [`CartContext.tsx`](frontend/src/context/CartContext.tsx) synchronizes cart state across the entire application without prop drilling.
* **Sticky Navigation Bar**: Sticky header featuring real-time cart item count badge updates.
* **Product Catalog**: Responsive grid displaying product photos, descriptions, badges, prices, stock levels, and interactive "Add to Cart" triggers.
* **Cart View**: Dedicated `/cart` page with line-item breakdown, quantity indicators, remove buttons, and automatic total price calculations.

---

## Project Structure

```text
TC/
├── pom.xml                               # Spring Boot Maven POM
├── src/
│   ├── main/
│   │   ├── java/com/tailorcards/api/
│   │   │   ├── config/                   # SecurityConfig
│   │   │   ├── controller/               # ProductController, CategoryController, CartController
│   │   │   ├── dto/                      # Request & Response records
│   │   │   ├── entity/                   # Product, Category, CartItem
│   │   │   ├── exception/                # GlobalExceptionHandler, ResourceNotFoundException
│   │   │   ├── mapper/                   # ProductMapper, CategoryMapper
│   │   │   ├── repository/               # Spring Data JPA Repositories
│   │   │   └── service/                  # ProductService, CategoryService, CartService
│   │   └── resources/
│   │       ├── application.yaml          # Datasource & JPA configuration
│   │       └── data.sql                  # Seed data (Categories & Products)
└── frontend/
    ├── package.json
    ├── vite.config.ts                    # Vite dev proxy configuration
    └── src/
        ├── components/                   # ProductList, Cart
        ├── context/                      # CartContext
        ├── hooks/                        # useCartSession
        ├── utils/                        # cartSession (localStorage UUID)
        ├── types.ts                      # TypeScript models
        ├── App.tsx                       # Layout, sticky Navbar, and Routes
        └── main.tsx                      # Root mounting with BrowserRouter
```

---

## Getting Started

### Prerequisites
* **Java 21** or higher
* **Node.js 20+** and **npm**
* **PostgreSQL** instance (local, Docker container, or hosted like Supabase)

---

### 1. Database & Backend Setup

1. **Configure Environment Variables**:
   Provide your database connection details using environment variables. You can set them in your terminal session or a `.env` file:

   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=tailorcards
   export DB_USERNAME=postgres
   export DB_PASSWORD=your_password
   ```

   *(Alternatively, provide a full JDBC URL using `export DB_URL=jdbc:postgresql://<host>:<port>/<dbname>` or `SPRING_DATASOURCE_URL`)*.

2. **Run the Spring Boot Application**:
   Execute the Maven wrapper from the project root:

   ```bash
   ./mvnw spring-boot:run
   ```

   The server starts on `http://localhost:8080`. Hibernate will automatically verify or create the tables, and [`data.sql`](src/main/resources/data.sql) will seed sample items.

---

### 2. Frontend Setup

1. **Navigate to the frontend directory**:
   ```bash
   cd frontend
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Start the Vite development server**:
   ```bash
   npm run dev
   ```

4. **Access the Application**:
   Open [http://localhost:5173](http://localhost:5173) in your browser. All `/api` requests will automatically route through Vite's dev proxy to `http://localhost:8080`.

---

## API Endpoints Overview

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/products` | Public | Paginated product catalog (`?page=0&size=10&sort=id,asc`) |
| `GET` | `/api/products/{id}` | Public | Single product details (404 if not found) |
| `POST` | `/api/products` | HTTP Basic | Create a new product |
| `GET` | `/api/categories` | Public | List all product categories |
| `POST` | `/api/categories` | HTTP Basic | Create a new category |
| `GET` | `/api/cart/{cartSessionId}` | Public | Retrieve active cart items and total price |
| `POST` | `/api/cart/{cartSessionId}` | Public | Add item to cart or increment quantity |
| `DELETE` | `/api/cart/{cartSessionId}/items/{itemId}` | Public | Remove specific item from cart |

---

## Future Roadmap

- [ ] **Checkout & Order Management**: Order processing service, order status tracking (Pending, Paid, Shipped, Delivered), and historical order lookups.
- [ ] **Payment Integration**: Secure payment gateway integration with Stripe (payment intents, webhooks, and card processing).
- [ ] **User Authentication (JWT)**: Customer registration, login, and token-based authentication via Spring Security with JWT.
- [ ] **Guest Cart Merging**: Automatic migration of guest items (`cartSessionId`) into registered customer accounts upon login.
- [ ] **Category Filtering & Search**: Interactive search bar and category filters in the React catalog.
- [ ] **Admin Dashboard**: Dedicated back-office interface for stock adjustment and product catalog management.
