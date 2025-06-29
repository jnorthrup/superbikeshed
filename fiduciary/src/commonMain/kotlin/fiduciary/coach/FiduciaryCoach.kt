package fiduciary.coach

import fiduciary.*

enum class FiduciaryDutyType {
    DUTY_OF_CARE,
    DUTY_OF_LOYALTY,
    DUTY_OF_IMPARTIALITY,
    DUTY_OF_PRUDENT_INVESTMENT,
    DUTY_TO_INFORM,
    DUTY_OF_CONFIDENTIALITY,
    DUTY_TO_ACCOUNT,
    DUTY_OF_DIVERSIFICATION,
    DUTY_TO_AVOID_CONFLICTS,
    DUTY_OF_GOOD_FAITH
}

object FiduciaryCoach {
    fun nagDuty(duty: FiduciaryDutyType): String = when (duty) {
        FiduciaryDutyType.DUTY_OF_CARE -> "You must act with the utmost care and diligence. No shortcuts. Document every decision."
        FiduciaryDutyType.DUTY_OF_LOYALTY -> "No self-dealing. Every action must benefit the beneficiaries, not you."
        FiduciaryDutyType.DUTY_OF_IMPARTIALITY -> "Treat all beneficiaries fairly. No favorites."
        FiduciaryDutyType.DUTY_OF_PRUDENT_INVESTMENT -> "Invest as a prudent expert would. Reckless speculation is forbidden."
        FiduciaryDutyType.DUTY_TO_INFORM -> "Keep beneficiaries informed. Silence is not an option."
        FiduciaryDutyType.DUTY_OF_CONFIDENTIALITY -> "Protect all sensitive information. Loose lips sink trusts."
        FiduciaryDutyType.DUTY_TO_ACCOUNT -> "Maintain detailed records. Every penny must be tracked."
        FiduciaryDutyType.DUTY_OF_DIVERSIFICATION -> "Don't put all eggs in one basket. Diversify assets."
        FiduciaryDutyType.DUTY_TO_AVOID_CONFLICTS -> "Disclose and avoid all conflicts of interest."
        FiduciaryDutyType.DUTY_OF_GOOD_FAITH -> "Act honestly and with integrity at all times."
        else -> "Unknown fiduciary duty. Review your obligations."
    }

    fun nagDutyForEntity(entityId: String, duty: FiduciaryDutyType): String =
        "[Entity: $entityId] " + nagDuty(duty)

    fun nagDutyForEntities(entityIds: List<String>, duty: FiduciaryDutyType): List<String> =
        entityIds.map { nagDutyForEntity(it, duty) }

    fun whipScenario(scenario: String): String = when (scenario) {
        "beneficiary_distribution" -> "Did you verify eligibility, document the request, and check for conflicts? If not, do it now."
        "investment_decision" -> "Have you reviewed the investment policy, checked for diversification, and documented your rationale? If not, stop and do it."
        "expense_approval" -> "Is this expense for the benefit of the trust? Is it reasonable and documented? If not, reject it."
        "reporting_period" -> "Are all records up to date? Have you prepared the required reports? No excuses."
        else -> "Unknown scenario. Review your duties and document everything."
    }

    fun whipScenarioForEntity(entityId: String, scenario: String): String =
        "[Entity: $entityId] " + whipScenario(scenario)

    fun whipScenarioForEntities(entityIds: List<String>, scenario: String): List<String> =
        entityIds.map { whipScenarioForEntity(it, scenario) }

    fun complianceReminder(): String = "Every action must be justified, documented, and defensible in court. If you can't explain it, don't do it."

    fun complianceReminderForEntity(entityId: String): String =
        "[Entity: $entityId] " + complianceReminder()

    fun complianceReminderForEntities(entityIds: List<String>): List<String> =
        entityIds.map { complianceReminderForEntity(it) }

    fun auditTrailNag(): String = "If it's not in the audit trail, it didn't happen. Log every action, every time."

    fun auditTrailNagForEntity(entityId: String): String =
        "[Entity: $entityId] " + auditTrailNag()

    fun auditTrailNagForEntities(entityIds: List<String>): List<String> =
        entityIds.map { auditTrailNagForEntity(it) }

    // TODO: Add more scenario-based nags and whip-cracking reminders as needed, supporting multi-entity/corp structures.
} 