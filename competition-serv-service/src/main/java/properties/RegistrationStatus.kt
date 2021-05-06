package properties

enum class RegistrationStatus(val status: String) {
    UNKNOWN("unknown"),
    PAYMENT_PENDING("payment_pending"),
    SUCCESS("success"),
    SUCCESS_UNACCEPTED("success_unaccepted"),
    SUCCESS_CONFIRMED("success_confirmed"),
    REFUSED("refused")
}