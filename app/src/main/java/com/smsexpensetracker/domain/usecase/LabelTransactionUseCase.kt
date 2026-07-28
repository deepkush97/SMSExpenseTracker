package com.smsexpensetracker.domain.usecase

class LabelTransactionUseCase {
    operator fun invoke(transactionId: Long, label: String): Result<Unit> {
        // TODO: implement later
        return Result.failure(NotImplementedError("Labeling not implemented yet"))
    }
}