# Project Overview

## License

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## About

Ledgee is an enterprise-grade ledger system built with Jakarta EE and Spring Framework. It provides a robust,
scalable solution for managing financial transactions and accounting records in a distributed system architecture.

## Introduction

Ledgee serves as a centralized ledger service that handles debit and credit records while maintaining strict
consistency and providing audit trails. Built on modern Java technologies, it offers REST APIs for seamless
integration with other systems.

## Principle

The system follows these key principles:

1. **Double-Entry Bookkeeping**: Every transaction affects at least two accounts, ensuring financial integrity
2. **Immutability**: Once recorded, entries cannot be modified - only compensating entries can be created
3. **Atomicity**: All operations are atomic, ensuring consistency across the system
4. **Audit Trail**: Complete history of all transactions is maintained
5. **RESTful Architecture**: Clean API design following REST principles
6. **Scalability**: Built to handle high transaction volumes in enterprise environments

## Modules

### Core Module

- Transaction processing engine
- Double-entry bookkeeping implementation
- Data persistence layer using Spring Data JPA

### API Module

- REST controllers for transaction processing
- Input validation and sanitization
- Response formatting and error handling

### Service Layer

- Business logic implementation
- Transaction management
- Account balance calculation

### Repository Layer

- Data access layer
- Entity definitions
- Database interaction

### Security Module

- Authentication and authorization
- Access control
- Audit logging

### Testing

- Unit tests
- Integration tests
- Performance tests using k6
- Load testing infrastructure

Each module is designed to be loosely coupled, following SOLID principles and clean architecture practices.