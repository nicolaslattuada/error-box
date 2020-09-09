package com.nicolattu

import arrow.core.getOrHandle
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec

class ErrorBoxTest : AnnotationSpec() {

    data class Person(val name: String, val city: String)
    data class Team(val persons: List<ErrorBox<Person>>)
    data class Country(val banks: List<BigBank>)
    data class Bank(val account: ErrorBox<Account>)
    data class BigBank(val accounts: List<ErrorBox<Account>>)
    data class Account(val balance: Balance, val name: String)
    data class Balance(val value: Int, val currency: Currency)
    data class Currency(val amount: Int, val sign: String)

    val objectMapper = jacksonObjectMapper().registerModule(ErrorBoxModule)

    val json = """
    {
      "balance": { "value": 12, "currency": {"amount": 25, "sign": "EUR"} },
      "name": "harry"
    }
""".trimIndent()
    val brokenJson = """
    {
      "balance": { "value": 12 },
      "name": "john"
    }
""".trimIndent()
    val bankJson = """
    {
      "account": $json
    }
""".trimIndent()
    val brokenBankJson = """
    {
      "account": $brokenJson
    }
""".trimIndent()

    val nullBankJson = """
    {
      "account": null
    }
""".trimIndent()


    val bigBankJson = """
    {
      "accounts": [$brokenJson, $json]
    }
""".trimIndent()

    val country = """
    {
      "banks": [$bigBankJson]
    }
""".trimIndent()

    val teamJson = """
{
    "persons": [
        {"name":  "Nicolas", "city":  "Barcelona"},
        {"name":  "Alex"}
    ]
}
""".trimIndent()

    @Test
    fun `should deserialize successfully list with invalid element`() {
        val team: Team = objectMapper.readValue(teamJson)
        team.persons
        val validPersons = team.persons.mapNotNull { errorBox ->
            errorBox.getOrHandle { error ->
                println(error.message)
                null
            }
        }
        validPersons shouldHaveSize 1
    }

    @Test
    fun `should deserialize successfully valid json`() {
        val account: ErrorBox<Account> = objectMapper.readValue(json)
        val maybeAccount = account.getOrHandle { null }
        maybeAccount?.name shouldBe "harry"
        maybeAccount?.balance?.value shouldBe 12
    }

    @Test
    fun `should fail to deserialize broken json`() {
        val account = objectMapper.readValue<ErrorBox<Account>>(brokenJson)
        val maybeAccount = account.getOrHandle {
            it.message shouldContain "value for creator parameter currency which is a non-nullable"
            null
        }
        maybeAccount shouldBe null
    }

    @Test
    fun `should deserialize successfully container with valid json`() {
        val bank = objectMapper.readValue<Bank>(bankJson)
        bank.account.map {
            it.balance.value shouldBe 12
        }
    }

    @Test
    fun `should deserialize container with broken json`() {
        val bank = objectMapper.readValue<Bank>(brokenBankJson)
        bank.account.mapLeft {
            it.message shouldContain "value for creator parameter currency which is a non-nullable"
        }
    }

    @Test
    fun `should deserialize container with null in the box`() {
        val bank = objectMapper.readValue<Bank>(nullBankJson)
        bank.account.mapLeft {
            it.message shouldBe "Could not deserialize null value"
        }
    }

    @Test
    fun `should get valid elements from mixed json`() {
        val bank = objectMapper.readValue<BigBank>(bigBankJson)
        bank.accounts shouldHaveSize 2
        val accountsClean = bank.accounts.mapNotNull { errorBox ->
            errorBox.getOrHandle { error ->
                println(error.message)
                null
            }
        }
        accountsClean shouldHaveSize 1
        accountsClean[0].balance.value shouldBe 12
        accountsClean[0].name shouldBe "harry"
    }

    @Test
    fun `should get valid elements from mixed json contained`() {
        val country = objectMapper.readValue<Country>(country)
        country.banks[0].accounts shouldHaveSize 2
        val accountsClean = country.banks[0].accounts.mapNotNull { errorBox ->
            errorBox.getOrHandle { error ->
                println(error.message)
                null
            }
        }
        accountsClean shouldHaveSize 1
        accountsClean[0].balance.value shouldBe 12
        accountsClean[0].name shouldBe "harry"
    }
}
