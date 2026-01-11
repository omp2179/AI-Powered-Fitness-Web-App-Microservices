# Fitness Microservices Application 🏋️‍♂️

A robust fitness tracking application built using a **Microservices Architecture** with **Spring Boot**. This project demonstrates scalable distributed systems with features like AI-powered recommendations, centralized configuration, and secure API gateways.

## 🏗 Architecture Overview

The backend is composed of several independent microservices:

| Service | Description | Port |
| :--- | :--- | :--- |
| **Eureka Server** | Service Discovery Server (Netflix Eureka). | `8761` |
| **Config Server** | Centralized configuration for all services. | `8888` |
| **API Gateway** | Entry point for the system. Handles routing & Security (OAuth2). | `8080` |
| **User Service** | Manages user registration and profiles. | *(Dynamic)* |
| **Activity Service** | Tracks user fitness activities (workouts, runs, etc.). | *(Dynamic)* |
| **AI Service** | Provides fitness recommendations using **Google Gemini AI**. | *(Dynamic)* |

## 🛠 Tech Stack

* **Language:** Java 24
* **Framework:** Spring Boot 3.5.4, Spring Cloud 2025.0.0
* **Service Discovery:** Netflix Eureka
* **Gateway:** Spring Cloud Gateway
* **Database:** MongoDB
* **Messaging:** Apache Kafka
* **Security:** Keycloak (OAuth2 / OpenID Connect)
* **AI Integration:** Google Gemini Model

## ⚙️ Prerequisites

Before running the project, ensure you have the following installed:

1.  **Java JDK 24**
2.  **MongoDB** (running locally or via Docker)
3.  **Apache Kafka** (running locally or via Docker)
4.  **Keycloak** (running on port `8181` with realm `fitness-app`)

## 🚀 Getting Started

Follow these steps to run the backend services locally.

### 1. Infrastructure Setup
Ensure your databases and identity providers are active:
* Start **MongoDB**
* Start **Kafka** (Zookeeper + Broker)
* Start **Keycloak** (ensure Realm `fitness-app` is configured)

### 2. Run Services
Start the microservices in the exact order below to ensure dependencies (like config and discovery) are available:

1.  **Eureka Server**
    ```bash
    cd eureka
    ./mvnw spring-boot:run
    ```
2.  **Config Server**
    ```bash
    cd configserver
    ./mvnw spring-boot:run
    ```
3.  **Microservices** (Open separate terminals for each)
    * **User Service**: `cd userservice && ./mvnw spring-boot:run`
    * **Activity Service**: `cd activityservice && ./mvnw spring-boot:run`
    * **AI Service**: `cd aiservice && ./mvnw spring-boot:run`
4.  **API Gateway**
    ```bash
    cd gateway
    ./mvnw spring-boot:run
    ```

## 🧠 Key Features

* **Secure Authentication**: Centralized security using Keycloak and Spring Security OAuth2.
* **Activity Tracking**: REST APIs to log and retrieve fitness activities.
* **Smart Recommendations**: The **AI Service** analyzes activity data to generate workout suggestions.
* **Event-Driven Architecture**: Uses Apache Kafka for asynchronous communication between services (e.g., activity updates triggering AI analysis).
* **Centralized Configuration**: All service configurations are managed externally via the Config Server.

## 📂 Project Structure

```bash
├── activityservice   # Fitness activity management (MongoDB + Kafka)
├── aiservice         # AI recommendations
├── configserver      # Centralized configuration files
├── eureka            # Service Registry
├── gateway           # API Gateway & Security
└── userservice       # User management
