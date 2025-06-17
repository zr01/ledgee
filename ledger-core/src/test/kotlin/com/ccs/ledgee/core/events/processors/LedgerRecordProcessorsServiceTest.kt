package com.ccs.ledgee.core.events.processors

import com.ccs.ledgee.commons.EventDetail
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.utils.uuidStr
import com.ccs.ledgee.events.LedgerEntriesReconciledEvent
import com.ccs.ledgee.events.LedgerEntryRecordedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LedgerRecordProcessorsServiceTest {

    private val service = LedgerRecordProcessorsService()

    @Test
    fun `should receive a staged debit event and output a reconciled event`() {
        val extRefId = "ext-ref-id"
        val event = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("dr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.DebitRecord.name)
            .build()
        val expectedRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(event)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(event))
            .build()

        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val result = service.processEventToRecord(
            extRefId,
            event,
            initRecord
        )

        assertThat(result.debitEntry?.publicId).isEqualTo(event.publicId)
        assertThat(result.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(result.creditEntry).isNull()
        assertThat(result.ledgerEntries.size).isEqualTo(1)
        assertThat(result).isEqualTo(expectedRecord)
    }

    @Test
    fun `should receive a staged credit event and output a reconciled event`() {
        val extRefId = "ext-ref-id"
        val event = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("cr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecord.name)
            .build()
        val expectedRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(event)
            .setDebitEntry(null)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(event))
            .build()

        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val result = service.processEventToRecord(
            extRefId,
            event,
            initRecord
        )

        assertThat(result.creditEntry?.publicId).isEqualTo(event.publicId)
        assertThat(result.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(result.debitEntry).isNull()
        assertThat(result.ledgerEntries.size).isEqualTo(1)
        assertThat(result).isEqualTo(expectedRecord)
    }

    @Test
    fun `should process a debit and credit to balance the records`() {
        val extRefId = "ext-ref-id"
        val credit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("cr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecord.name)
            .build()
        val debit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("dr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.DebitRecord.name)
            .build()

        val expectedDebitRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit))
            .build()
        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val debitResult = service.processEventToRecord(
            extRefId,
            debit,
            initRecord
        )

        assertThat(debitResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(debitResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult.creditEntry).isNull()
        assertThat(debitResult.ledgerEntries.size).isEqualTo(1)
        assertThat(debitResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult).isEqualTo(expectedDebitRecord)

        val expectedCreditRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(credit)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.Balanced.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit))
            .build()

        val creditResult = service.processEventToRecord(
            extRefId,
            credit,
            debitResult
        )

        assertThat(creditResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(creditResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(creditResult.creditEntry?.publicId).isEqualTo(credit.publicId)
        assertThat(creditResult.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(creditResult.ledgerEntries.size).isEqualTo(2)
        assertThat(creditResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(creditResult).isEqualTo(expectedCreditRecord)
    }

    @Test
    fun `should process a debit and credit to unbalance state`() {
        val extRefId = "ext-ref-id"
        val credit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(101)
            .setDescription("description")
            .setPublicId("cr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecord.name)
            .build()
        val debit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("dr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.DebitRecord.name)
            .build()

        val expectedDebitRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit))
            .build()
        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val debitResult = service.processEventToRecord(
            extRefId,
            debit,
            initRecord
        )

        assertThat(debitResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(debitResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult.creditEntry).isNull()
        assertThat(debitResult.ledgerEntries.size).isEqualTo(1)
        assertThat(debitResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult).isEqualTo(expectedDebitRecord)

        val expectedCreditRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(credit)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.Unbalanced.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit))
            .build()

        val creditResult = service.processEventToRecord(
            extRefId,
            credit,
            debitResult
        )

        assertThat(creditResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(creditResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.creditEntry?.publicId).isEqualTo(credit.publicId)
        assertThat(creditResult.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.ledgerEntries.size).isEqualTo(2)
        assertThat(creditResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult).isEqualTo(expectedCreditRecord)
        assertThat(creditResult.creditEntry.eventDetail.metadata).containsKey("error")
    }

    @Test
    fun `should process a debit and credit to void state`() {
        val extRefId = "ext-ref-id"
        val credit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(101)
            .setDescription("description")
            .setPublicId("cr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecord.name)
            .build()
        val debit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("dr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.DebitRecord.name)
            .build()

        val expectedDebitRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit))
            .build()
        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val debitResult = service.processEventToRecord(
            extRefId,
            debit,
            initRecord
        )

        assertThat(debitResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(debitResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult.creditEntry).isNull()
        assertThat(debitResult.ledgerEntries.size).isEqualTo(1)
        assertThat(debitResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult).isEqualTo(expectedDebitRecord)

        val expectedCreditRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(credit)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.Unbalanced.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit))
            .build()

        val creditResult = service.processEventToRecord(
            extRefId,
            credit,
            debitResult
        )

        assertThat(creditResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(creditResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.creditEntry?.publicId).isEqualTo(credit.publicId)
        assertThat(creditResult.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.ledgerEntries.size).isEqualTo(2)
        assertThat(creditResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult).isEqualTo(expectedCreditRecord)
        assertThat(creditResult.creditEntry.eventDetail.metadata).containsKey("error")

        val void = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(101)
            .setDescription("description")
            .setPublicId("cr-0002")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecordVoid.name)
            .build()

        val expectedCreditRecordVoid = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit, void))
            .build()

        val voidResult = service.processEventToRecord(
            extRefId,
            void,
            creditResult
        )

        assertThat(voidResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(voidResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(voidResult.creditEntry).isNull()
        assertThat(voidResult.ledgerEntries.size).isEqualTo(3)
        assertThat(voidResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(credit.recordStatus).isEqualTo(LedgerRecordStatus.Void.name)
        assertThat(voidResult).isEqualTo(expectedCreditRecordVoid)
    }

    @Test
    fun `should process a debit and credit to void then balanced state`() {
        val extRefId = "ext-ref-id"
        val credit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(101)
            .setDescription("description")
            .setPublicId("cr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecord.name)
            .build()
        val debit = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("dr-0001")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.DebitRecord.name)
            .build()

        val expectedDebitRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit))
            .build()
        val initRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setDebitEntry(null)
            .setCreditEntry(null)
            .setLedgerEntries(mutableListOf())
            .setReconciliationStatus(LedgerRecordStatus.Staged.name)
            .build()

        val debitResult = service.processEventToRecord(
            extRefId,
            debit,
            initRecord
        )

        assertThat(debitResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(debitResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult.creditEntry).isNull()
        assertThat(debitResult.ledgerEntries.size).isEqualTo(1)
        assertThat(debitResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(debitResult).isEqualTo(expectedDebitRecord)

        val expectedCreditRecord = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(credit)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.Unbalanced.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit))
            .build()

        val creditResult = service.processEventToRecord(
            extRefId,
            credit,
            debitResult
        )

        assertThat(creditResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(creditResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.creditEntry?.publicId).isEqualTo(credit.publicId)
        assertThat(creditResult.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult.ledgerEntries.size).isEqualTo(2)
        assertThat(creditResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(creditResult).isEqualTo(expectedCreditRecord)
        assertThat(creditResult.creditEntry.eventDetail.metadata).containsKey("error")

        val void = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(101)
            .setDescription("description")
            .setPublicId("cr-0002")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecordVoid.name)
            .build()

        val expectedCreditRecordVoid = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(null)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.WaitingForPair.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit, void))
            .build()

        val voidResult = service.processEventToRecord(
            extRefId,
            void,
            creditResult
        )

        assertThat(voidResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(voidResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Unbalanced.name)
        assertThat(voidResult.creditEntry).isNull()
        assertThat(voidResult.ledgerEntries.size).isEqualTo(3)
        assertThat(voidResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.WaitingForPair.name)
        assertThat(credit.recordStatus).isEqualTo(LedgerRecordStatus.Void.name)
        assertThat(voidResult).isEqualTo(expectedCreditRecordVoid)

        val correction = LedgerEntryRecordedEvent.newBuilder()
            .setRecordStatus(LedgerRecordStatus.Staged.name)
            .setAmount(100)
            .setDescription("description")
            .setPublicId("cr-0003")
            .setPublicAccountId("ac-0001")
            .setTransactionOn(Instant.now().epochSecond)
            .setExternalReferenceId(extRefId)
            .setIsPending(IsPending.No.name)
            .setEventDetail(eventDetail())
            .setEntryType(LedgerEntryType.CreditRecordCorrection.name)
            .build()

        val expectedCreditRecordCorrection = LedgerEntriesReconciledEvent.newBuilder()
            .setCreditEntry(correction)
            .setDebitEntry(debit)
            .setReconciliationStatus(LedgerRecordStatus.Balanced.name)
            .setLedgerEntries(mutableListOf<Any>(debit, credit, void, correction))
            .build()

        val correctionResult = service.processEventToRecord(
            extRefId,
            correction,
            voidResult
        )

        assertThat(correctionResult.debitEntry?.publicId).isEqualTo(debit.publicId)
        assertThat(correctionResult.debitEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(correctionResult.creditEntry?.publicId).isEqualTo(correction.publicId)
        assertThat(correctionResult.creditEntry?.recordStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(correctionResult.ledgerEntries.size).isEqualTo(4)
        assertThat(correctionResult.reconciliationStatus).isEqualTo(LedgerRecordStatus.Balanced.name)
        assertThat(correctionResult).isEqualTo(expectedCreditRecordCorrection)
    }
}

private fun eventDetail(): EventDetail = EventDetail.newBuilder()
    .setEventBy("test")
    .setEventId(uuidStr())
    .setEventOn(Instant.now().epochSecond)
    .setMetadata(mutableMapOf())
    .build()