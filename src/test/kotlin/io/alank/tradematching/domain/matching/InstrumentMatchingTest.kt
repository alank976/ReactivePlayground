package io.alank.tradematching.domain.matching

import io.alank.tradematching.domain.account.AccountGroup
import io.alank.tradematching.domain.trade.BuySellTradeEvent
import io.alank.tradematching.domain.trade.EndOfTradeStreamEvent
import io.alank.tradematching.domain.trade.Trade
import io.alank.tradematching.domain.trade.Way.B
import io.alank.tradematching.domain.trade.Way.S
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class InstrumentMatchingTest {

    @Test
    fun `test handle when brand new state`() {
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC")
        val givenTrade = Trade(id = "1",
                ticker = "HSBC",
                way = B,
                price = 1.0,
                quantity = 10)

        val actual = instrumentMatching.handle(BuySellTradeEvent(givenTrade))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC",
                        buyTrades = listOf(givenTrade),
                        sellTrades = listOf(),
                        results = listOf()))
    }

    @Test
    fun `test handle when a buy exists and it had a matched result from last match, and a new buy comes in, expect appended into buyTrades and results`() {
        val givenTrade = Trade(id = "1",
                ticker = "HSBC",
                way = B,
                price = 1.0,
                quantity = 8)
        val givenMatchedResult = MatchedResult(buyTradeId = "1",
                buyPrice = 1.0,
                sellTradeId = "2",
                sellPrice = 2.0,
                quantity = 2,
                accountGroup = AccountGroup())
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC", buyTrades = listOf(givenTrade), results = listOf(givenMatchedResult))

        val newTrade = givenTrade.copy(id = "3", way = B, quantity = 5)
        val actual = instrumentMatching.handle(BuySellTradeEvent(newTrade))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(givenTrade, newTrade),
                        sellTrades = listOf(),
                        results = listOf()))
    }

    @Test
    fun `test handle when a buy exists and a sell comes in, expect a matched result `() {
        val givenTrade = Trade(id = "1",
                ticker = "HSBC",
                way = B,
                price = 1.0,
                quantity = 10)
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC", buyTrades = listOf(givenTrade))

        val newSellTrade = givenTrade.copy(id = "2", way = S)
        val actual = instrumentMatching.handle(BuySellTradeEvent(newSellTrade))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(),
                        sellTrades = listOf(),
                        results = listOf(MatchedResult("1", 1.0, "2", 1.0, 10, AccountGroup()))))
    }

    @Test
    fun `test handle when a smaller buy exists and a sell comes in, expect a matched result and sell trade residual`() {
        val givenTrade = Trade(id = "1",
                ticker = "HSBC",
                way = B,
                price = 1.0,
                quantity = 2)
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC", buyTrades = listOf(givenTrade))

        val newSellTrade = givenTrade.copy(id = "2", way = S, quantity = 10)
        val actual = instrumentMatching.handle(BuySellTradeEvent(newSellTrade))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(),
                        sellTrades = listOf(newSellTrade.copy(quantity = 8)),
                        results = listOf(MatchedResult("1", 1.0, "2", 1.0, 2, AccountGroup()))))
    }

    @Test
    fun `test handle when more than 1 buy exist, whose total quantity is greater, and a sell comes in, expect matched result and residual buy trades remain`() {
        val buyTrades = listOf(
                Trade(id = "1",
                        ticker = "HSBC",
                        way = B,
                        price = 1.0,
                        quantity = 2),
                Trade(id = "2",
                        ticker = "HSBC",
                        way = B,
                        price = 2.0,
                        quantity = 3))
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC", buyTrades = buyTrades)
        val sellTrade = Trade(id = "3",
                ticker = "HSBC",
                way = S,
                price = 1.5,
                quantity = 3)

        val actual = instrumentMatching.handle(BuySellTradeEvent(sellTrade))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(Trade(id = "2",
                                ticker = "HSBC",
                                way = B,
                                price = 2.0,
                                quantity = 2)),
                        sellTrades = listOf(),
                        results = listOf(MatchedResult("1", 1.0, "3", 1.5, 2, AccountGroup()),
                                MatchedResult("2", 2.0, "3", 1.5, 1, AccountGroup()))))
    }

    @Test
    fun `test handle end of trade event, expect make unmatched results for remaining trades`() {
        val buyTrades = listOf(
                Trade(id = "1",
                        ticker = "HSBC",
                        way = B,
                        price = 1.0,
                        quantity = 2),
                Trade(id = "2",
                        ticker = "HSBC",
                        way = B,
                        price = 2.0,
                        quantity = 3))
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC", buyTrades = buyTrades)

        val actual = instrumentMatching.handle(EndOfTradeStreamEvent(0.0))
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(),
                        sellTrades = listOf(),
                        results = listOf(UnmatchedResult("1", B, 1.0, 0.0, 2, AccountGroup()),
                                UnmatchedResult("2", B, 2.0, 0.0, 3, AccountGroup()))))
    }

    @Test
    fun `test handle end of trade event, when no remaining buy or sell trades inside, expect no unmatched results`() {
        val instrumentMatching = InstrumentMatching(accountGroup = AccountGroup(), ticker = "HSBC")

        val actual = instrumentMatching.handle(EndOfTradeStreamEvent())
        assertThat(actual)
                .isEqualTo(InstrumentMatching(
                        accountGroup = AccountGroup(),
                        ticker = "HSBC",
                        buyTrades = listOf(),
                        sellTrades = listOf(),
                        results = listOf()))
    }
}