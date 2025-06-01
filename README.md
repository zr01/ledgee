# Distributed Double-Entry Ledger System

A scalable, distributed double-entry bookkeeping system built with Jakarta EE and Spring Data JPA, designed to handle
high-throughput financial transactions with guaranteed consistency and auditability.

## Overview

This ledger system implements double-entry bookkeeping principles in a distributed environment, ensuring:

- Atomic transaction processing
- Eventual consistency for balanced books
- Audit trail preservation
- Scalable architecture using message-driven second entries

The system is inspired
by [Stripe's Ledger design](https://stripe.com/blog/ledger-stripe-system-for-tracking-and-validating-money-movement) and
follows established accounting principles described
in [Double-Entry Accounting for Engineers](https://anvil.works/blog/double-entry-accounting-for-engineers).

## Core Concepts

### Double-Entry Model

- Every transaction affects at least two accounts
- Total debits must equal total credits
- Accounts maintain running balances
- All records are immutable (append-only)

### Transaction Processing Flow

1. Synchronous first entry processing
2. Message publication for corresponding entries
3. Asynchronous processing of balancing entries
4. Continuous balance verification

## Technical Stack

- Java 21
- Jakarta EE
- Spring Data JPA
- Kotlin 1.9
- Message Queue System (implementation configurable)
- Relational Database (implementation configurable)

## Getting Started

### Prerequisites

```bash
- Java 21 or higher
- Maven 3.8+
- Docker (for local development)
```

### Building the Project

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

## Extension Points

The system is designed to be extensible through additional modules for specific needs:

- Custom validation rules
- Different messaging implementations
- Additional account types
- Specialized reporting
- Integration with external systems

## Design Principles

- **Immutability**: All financial records are append-only
- **Eventual Consistency**: System guarantees balanced books over time
- **Audit Trail**: Complete history of all transactions
- **Scalability**: Horizontal scaling through message-driven architecture
- **Extensibility**: Core functionality can be extended through additional modules

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

```
Copyright 2025 Camel Case Studio
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
`http://www.apache.org/licenses/LICENSE-2.0 `
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
```

