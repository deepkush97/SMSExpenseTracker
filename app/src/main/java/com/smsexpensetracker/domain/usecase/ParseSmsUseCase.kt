package com.smsexpensetracker.domain.usecase

class ParseSmsUseCase {
    operator fun invoke(smsBody: String, sender: String): Result<Unit> {
        // TODO: implement later
        return Result.failure(NotImplementedError("Parser not implemented yet"))
    }
}