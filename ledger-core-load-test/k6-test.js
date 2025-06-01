import http from "k6/http"
import {check} from "k6"
import {uuidv4} from "https://jslib.k6.io/k6-utils/1.4.0/index.js"

export const options = {
    stages: [
        {duration: "30s", target: 20},
        {duration: "1m30s", target: 10},
        {duration: "20s", target: 0},
    ],
}

const accountProductCode = ["banking", "creditcard"]
const merchantAccounts = ["merch-1", "merch-2", "merch-3"]

export default function () {
    const accountId = Math.floor(Math.random() * 10000) + 1
    const headers = {
        "Content-Type": "application/json"
    }
    const payload = {
        type: "LedgerEntry",
        data: {
            amount: Math.floor(Math.random() * 100000) + 10000,
            productCode: accountProductCode[Math.floor(Math.random() * 2)],
            description: `test-${uuidv4()}`,
            createdBy: "load-test",
            externalReferenceId: `ref-${uuidv4()}`,
            isPending: false
        }
    }

    const debitResponse = http.post(
        `http://localhost:8080/api/v1/accounts/${accountId}/DebitRecord`,
        JSON.stringify(payload),
        {headers}
    )

    check(debitResponse, {
        "status is 201": (r) => r.status === 201
    })

    const creditResponse = http.post(
        `http://localhost:8080/api/v1/accounts/${merchantAccounts[Math.floor(Math.random() * 3)]}/CreditRecord`,
        JSON.stringify({
            ...payload,
            productCode: "business"
        }),
        {headers}
    )

    check(creditResponse, {
        "status is 201": (r) => r.status === 201
    })
}