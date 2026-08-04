# Searchable Category Icon Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 14-icon category picker with a searchable grid of ~120-150 curated Material icons.

**Architecture:** A single source-of-truth catalog `CATEGORY_ICONS: List<IconEntry>` (name, keywords, vector) in `CategoryIcons.kt` replaces `CATEGORY_ICON_NAMES` + the `materialIcon()` `when`. `materialIcon()` becomes a catalog lookup (backward compatible — all 14 legacy keys stay). A pure `searchIcons(query)` filters name+keywords. `CategoryDialog` swaps its icon `FlowRow` for a search field + scrollable `LazyVerticalGrid`.

**Tech Stack:** Kotlin, Compose M3, material-icons-extended (already a dependency).

## Global Constraints

- Package root: `com.smsexpensetracker`. Money is paisa `Long` (`formatPaisa` from `ui/util`). No code comments unless a task's code block includes them.
- Build gate (no lint/typecheck): `./gradlew assembleDebug` and `./gradlew cleanTestDebugUnitTest testDebugUnitTest`.
- Test baseline: 354 tests green.
- Storage contract unchanged: the DB stores the icon as a **string name** (`Category.icon`); the picker is presentation-only.
- All 14 legacy seed icon keys MUST stay in the catalog: `restaurant, shopping_cart, local_gas_station, receipt, shopping_bag, movie, local_hospital, directions_car, school, home, flight, payments, trending_up, category` (from `SeedDatabaseCallback.kt:38-51`).
- New-category default icon = `CATEGORY_ICONS.first().name` (must remain "restaurant").
- `CATEGORY_COLORS` unchanged.
- Commit directly to `main`. NEVER stage the pre-existing dirty `app/src/main/java/com/smsexpensetracker/ui/screens/dashboard/DashboardViewModel.kt`, `opencode.json`, or untracked plan/spec docs.

---

### Task 1: Icon catalog + `searchIcons` + `materialIcon` lookup

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt`
- Test: `app/src/test/java/com/smsexpensetracker/ui/util/CategoryIconsTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks; existing `Category` domain model only.
- Produces: `data class IconEntry(name: String, keywords: List<String>, imageVector: ImageVector)`; `val CATEGORY_ICONS: List<IconEntry>`; `fun materialIcon(name: String): ImageVector`; `fun searchIcons(query: String): List<IconEntry>`. `CATEGORY_ICON_NAMES` is kept as a derived `val` so `CategoryDialog` (Task 2's file) still compiles this commit.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/smsexpensetracker/ui/util/CategoryIconsTest.kt`:

```kotlin
package com.smsexpensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconsTest {

    @Test
    fun `catalog has between 120 and 150 entries`() {
        assertTrue(CATEGORY_ICONS.size in 120..150)
    }

    @Test
    fun `every icon name is unique`() {
        val names = CATEGORY_ICONS.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `catalog includes all 14 legacy seed icon keys`() {
        val names = CATEGORY_ICONS.map { it.name }
        listOf(
            "restaurant", "shopping_cart", "local_gas_station", "receipt",
            "shopping_bag", "movie", "local_hospital", "directions_car",
            "school", "home", "flight", "payments", "trending_up", "category"
        ).forEach { assertTrue("missing $it", it in names) }
    }

    @Test
    fun `empty query returns the full catalog`() {
        assertEquals(CATEGORY_ICONS, searchIcons(""))
        assertEquals(CATEGORY_ICONS, searchIcons("   "))
    }

    @Test
    fun `search matches an icon name`() {
        assertTrue(searchIcons("home").any { it.name == "home" })
    }

    @Test
    fun `search matches a keyword alias`() {
        assertTrue(searchIcons("food").any { it.name == "restaurant" })
    }

    @Test
    fun `search is case insensitive`() {
        assertTrue(searchIcons("FOOD").any { it.name == "restaurant" })
    }

    @Test
    fun `search ignores underscores`() {
        assertTrue(searchIcons("shoppingcart").any { it.name == "shopping_cart" })
    }

    @Test
    fun `search returns empty for no match`() {
        assertTrue(searchIcons("zzzznotanicon").isEmpty())
    }

    @Test
    fun `materialIcon returns the vector for a known name`() {
        assertSame(Icons.Filled.Restaurant, materialIcon("restaurant"))
    }

    @Test
    fun `materialIcon falls back to category icon for unknown name`() {
        assertSame(Icons.Filled.Category, materialIcon("does_not_exist"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.CategoryIconsTest" -v`
Expected: COMPILATION ERROR — `CATEGORY_ICONS` and `searchIcons` do not exist.

- [ ] **Step 3: Write the implementation**

Replace the entire contents of `app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt` with the following. The 14 legacy keys are first (in the old `CATEGORY_ICON_NAMES` order), so `CATEGORY_ICONS.first().name == "restaurant"`:

```kotlin
package com.smsexpensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CarRental
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkout
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CrueltyFree
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Hospital
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.HouseSiding
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Light
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Living
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Mask
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Microphone
import androidx.compose.material.icons.filled.Microwave
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Monitoring
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.OtherHouses
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Paw
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Tapas
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.smsexpensetracker.domain.model.Category

val CATEGORY_COLORS = listOf(
    -13108, -13956304, -48060, -13676760, -10496, -16581634,
    -14513374, -12664161, -4880347, -7084816, -7829368, -10980385
)

data class IconEntry(
    val name: String,
    val keywords: List<String>,
    val imageVector: ImageVector
)

val CATEGORY_ICONS: List<IconEntry> = listOf(
    IconEntry("restaurant", listOf("food", "eat", "dining"), Icons.Filled.Restaurant),
    IconEntry("shopping_cart", listOf("cart", "buy", "grocery", "store"), Icons.Filled.ShoppingCart),
    IconEntry("local_gas_station", listOf("fuel", "gas", "petrol", "car"), Icons.Filled.LocalGasStation),
    IconEntry("receipt", listOf("bill", "receipt", "utility"), Icons.Filled.Receipt),
    IconEntry("shopping_bag", listOf("shopping", "bag", "store"), Icons.Filled.ShoppingBag),
    IconEntry("movie", listOf("movie", "film", "cinema"), Icons.Filled.Movie),
    IconEntry("local_hospital", listOf("hospital", "health", "medical"), Icons.Filled.LocalHospital),
    IconEntry("directions_car", listOf("car", "vehicle", "drive"), Icons.Filled.DirectionsCar),
    IconEntry("school", listOf("school", "education", "study"), Icons.Filled.School),
    IconEntry("home", listOf("home", "house", "rent"), Icons.Filled.Home),
    IconEntry("flight", listOf("flight", "plane", "airplane"), Icons.Filled.Flight),
    IconEntry("payments", listOf("payment", "salary", "money"), Icons.Filled.Payments),
    IconEntry("trending_up", listOf("trend", "growth", "increase", "investment"), Icons.Filled.TrendingUp),
    IconEntry("category", listOf("other", "category", "general"), Icons.Filled.Category),

    IconEntry("local_cafe", listOf("cafe", "coffee", "tea"), Icons.Filled.LocalCafe),
    IconEntry("coffee", listOf("coffee", "brew"), Icons.Filled.Coffee),
    IconEntry("cake", listOf("cake", "birthday", "dessert"), Icons.Filled.Cake),
    IconEntry("icecream", listOf("ice", "cream", "dessert"), Icons.Filled.Icecream),
    IconEntry("fastfood", listOf("fast", "food", "burger"), Icons.Filled.Fastfood),
    IconEntry("ramen_dining", listOf("ramen", "noodle", "soup"), Icons.Filled.RamenDining),
    IconEntry("lunch_dining", listOf("lunch", "meal", "food"), Icons.Filled.LunchDining),
    IconEntry("dinner_dining", listOf("dinner", "meal"), Icons.Filled.DinnerDining),
    IconEntry("bakery_dining", listOf("bakery", "bread"), Icons.Filled.BakeryDining),
    IconEntry("set_meal", listOf("meal", "set", "food"), Icons.Filled.SetMeal),
    IconEntry("kebab_dining", listOf("kebab", "grill", "food"), Icons.Filled.KebabDining),
    IconEntry("rice_bowl", listOf("rice", "bowl", "asian"), Icons.Filled.RiceBowl),
    IconEntry("tapas", listOf("tapas", "small", "snack"), Icons.Filled.Tapas),
    IconEntry("egg", listOf("egg", "breakfast"), Icons.Filled.Egg),
    IconEntry("egg_alt", listOf("egg", "breakfast"), Icons.Filled.EggAlt),
    IconEntry("soup_kitchen", listOf("soup", "food"), Icons.Filled.SoupKitchen),
    IconEntry("local_pizza", listOf("pizza", "food"), Icons.Filled.LocalPizza),
    IconEntry("cookie", listOf("cookie", "dessert", "snack"), Icons.Filled.Cookie),
    IconEntry("local_bar", listOf("bar", "drink", "alcohol"), Icons.Filled.LocalBar),
    IconEntry("wine_bar", listOf("wine", "bar"), Icons.Filled.WineBar),
    IconEntry("liquor", listOf("liquor", "drink"), Icons.Filled.Liquor),
    IconEntry("storefront", listOf("store", "shop"), Icons.Filled.Storefront),
    IconEntry("store", listOf("store", "shop", "retail"), Icons.Filled.Store),
    IconEntry("local_mall", listOf("mall", "shopping", "store"), Icons.Filled.LocalMall),
    IconEntry("add_shopping_cart", listOf("cart", "add", "buy"), Icons.Filled.AddShoppingCart),
    IconEntry("remove_shopping_cart", listOf("cart", "remove"), Icons.Filled.RemoveShoppingCart),
    IconEntry("checkout", listOf("checkout", "pay", "buy"), Icons.Filled.Checkout),
    IconEntry("sell", listOf("sell", "sale", "price"), Icons.Filled.Sell),
    IconEntry("local_offer", listOf("offer", "tag", "discount", "sale"), Icons.Filled.LocalOffer),
    IconEntry("card_giftcard", listOf("gift", "giftcard", "card"), Icons.Filled.CardGiftcard),
    IconEntry("redeem", listOf("gift", "redeem", "reward"), Icons.Filled.Redeem),
    IconEntry("loyalty", listOf("loyalty", "points", "reward"), Icons.Filled.Loyalty),
    IconEntry("local_taxi", listOf("taxi", "cab", "car"), Icons.Filled.LocalTaxi),
    IconEntry("airport_shuttle", listOf("shuttle", "airport", "bus"), Icons.Filled.AirportShuttle),
    IconEntry("train", listOf("train", "rail"), Icons.Filled.Train),
    IconEntry("tram", listOf("tram", "transit"), Icons.Filled.Tram),
    IconEntry("subway", listOf("subway", "metro", "train"), Icons.Filled.Subway),
    IconEntry("directions_bus", listOf("bus", "transit"), Icons.Filled.DirectionsBus),
    IconEntry("directions_bike", listOf("bike", "cycle", "bicycle"), Icons.Filled.DirectionsBike),
    IconEntry("directions_walk", listOf("walk", "pedestrian"), Icons.Filled.DirectionsWalk),
    IconEntry("two_wheeler", listOf("bike", "scooter", "motorcycle"), Icons.Filled.TwoWheeler),
    IconEntry("electric_car", listOf("electric", "car", "ev"), Icons.Filled.ElectricCar),
    IconEntry("car_rental", listOf("car", "rental", "rent"), Icons.Filled.CarRental),
    IconEntry("car_repair", listOf("car", "repair", "service"), Icons.Filled.CarRepair),
    IconEntry("local_shipping", listOf("shipping", "delivery", "truck"), Icons.Filled.LocalShipping),
    IconEntry("delivery_dining", listOf("delivery", "food", "scooter"), Icons.Filled.DeliveryDining),
    IconEntry("flight_takeoff", listOf("flight", "takeoff", "depart"), Icons.Filled.FlightTakeoff),
    IconEntry("flight_land", listOf("flight", "landing", "arrive"), Icons.Filled.FlightLand),
    IconEntry("luggage", listOf("luggage", "bag", "travel"), Icons.Filled.Luggage),
    IconEntry("local_parking", listOf("parking", "park"), Icons.Filled.LocalParking),
    IconEntry("ev_station", listOf("ev", "charge", "electric"), Icons.Filled.EvStation),
    IconEntry("medical_services", listOf("medical", "health", "care"), Icons.Filled.MedicalServices),
    IconEntry("medication", listOf("medication", "medicine", "pill"), Icons.Filled.Medication),
    IconEntry("vaccines", listOf("vaccine", "shot", "health"), Icons.Filled.Vaccines),
    IconEntry("health_and_safety", listOf("health", "safety", "shield"), Icons.Filled.HealthAndSafety),
    IconEntry("local_pharmacy", listOf("pharmacy", "medicine", "drug"), Icons.Filled.LocalPharmacy),
    IconEntry("healing", listOf("healing", "health", "care"), Icons.Filled.Healing),
    IconEntry("monitor_heart", listOf("heart", "monitor", "health"), Icons.Filled.MonitorHeart),
    IconEntry("favorite", listOf("heart", "favorite", "love"), Icons.Filled.Favorite),
    IconEntry("favorite_border", listOf("heart", "favorite", "outline"), Icons.Filled.FavoriteBorder),
    IconEntry("bloodtype", listOf("blood", "type", "donate"), Icons.Filled.Bloodtype),
    IconEntry("mask", listOf("mask", "health", "face"), Icons.Filled.Mask),
    IconEntry("coronavirus", listOf("virus", "sick", "health"), Icons.Filled.Coronavirus),
    IconEntry("sick", listOf("sick", "ill", "health"), Icons.Filled.Sick),
    IconEntry("pets", listOf("pets", "animal", "dog"), Icons.Filled.Pets),
    IconEntry("paw", listOf("paw", "animal", "pet"), Icons.Filled.Paw),
    IconEntry("cruelty_free", listOf("animal", "care", "rabbit"), Icons.Filled.CrueltyFree),
    IconEntry("account_balance", listOf("bank", "balance", "finance"), Icons.Filled.AccountBalance),
    IconEntry("account_balance_wallet", listOf("wallet", "money", "balance"), Icons.Filled.AccountBalanceWallet),
    IconEntry("savings", listOf("savings", "money", "save", "piggy"), Icons.Filled.Savings),
    IconEntry("wallet", listOf("wallet", "money", "purse"), Icons.Filled.Wallet),
    IconEntry("currency_exchange", listOf("exchange", "currency", "money"), Icons.Filled.CurrencyExchange),
    IconEntry("currency_rupee", listOf("rupee", "india", "currency"), Icons.Filled.CurrencyRupee),
    IconEntry("price_change", listOf("price", "change", "stock"), Icons.Filled.PriceChange),
    IconEntry("price_check", listOf("price", "check", "tag"), Icons.Filled.PriceCheck),
    IconEntry("credit_card", listOf("card", "credit", "payment"), Icons.Filled.CreditCard),
    IconEntry("attach_money", listOf("money", "cash", "currency"), Icons.Filled.AttachMoney),
    IconEntry("paid", listOf("paid", "money", "pay"), Icons.Filled.Paid),
    IconEntry("request_quote", listOf("quote", "estimate", "bill"), Icons.Filled.RequestQuote),
    IconEntry("cottage", listOf("cottage", "house", "home"), Icons.Filled.Cottage),
    IconEntry("house_siding", listOf("house", "siding", "home"), Icons.Filled.HouseSiding),
    IconEntry("home_work", listOf("home", "work", "office"), Icons.Filled.HomeWork),
    IconEntry("apartment", listOf("apartment", "building", "housing"), Icons.Filled.Apartment),
    IconEntry("other_houses", listOf("house", "housing", "property"), Icons.Filled.OtherHouses),
    IconEntry("real_estate_agent", listOf("estate", "agent", "property"), Icons.Filled.RealEstateAgent),
    IconEntry("holiday_village", listOf("holiday", "vacation", "house"), Icons.Filled.HolidayVillage),
    IconEntry("chair", listOf("chair", "furniture"), Icons.Filled.Chair),
    IconEntry("weekend", listOf("sofa", "furniture", "living"), Icons.Filled.Weekend),
    IconEntry("kitchen", listOf("kitchen", "cook", "home"), Icons.Filled.Kitchen),
    IconEntry("microwave", listOf("microwave", "appliance", "kitchen"), Icons.Filled.Microwave),
    IconEntry("lightbulb", listOf("light", "bulb", "energy"), Icons.Filled.Lightbulb),
    IconEntry("light", listOf("light", "lamp"), Icons.Filled.Light),
    IconEntry("bed", listOf("bed", "sleep", "furniture"), Icons.Filled.Bed),
    IconEntry("king_bed", listOf("bed", "sleep", "king"), Icons.Filled.KingBed),
    IconEntry("living", listOf("living", "room", "home"), Icons.Filled.Living),
    IconEntry("electrical_services", listOf("electric", "power", "utility"), Icons.Filled.ElectricalServices),
    IconEntry("water_drop", listOf("water", "drop", "utility"), Icons.Filled.WaterDrop),
    IconEntry("bolt", listOf("bolt", "energy", "electric"), Icons.Filled.Bolt),
    IconEntry("cleaning_services", listOf("cleaning", "clean", "service"), Icons.Filled.CleaningServices),
    IconEntry("local_laundry_service", listOf("laundry", "wash", "clean"), Icons.Filled.LocalLaundryService),
    IconEntry("phone", listOf("phone", "mobile", "call"), Icons.Filled.Phone),
    IconEntry("wifi", listOf("wifi", "internet", "network"), Icons.Filled.Wifi),
    IconEntry("menu_book", listOf("book", "learn", "education"), Icons.Filled.MenuBook),
    IconEntry("book", listOf("book", "read", "education"), Icons.Filled.Book),
    IconEntry("auto_stories", listOf("book", "story", "read"), Icons.Filled.AutoStories),
    IconEntry("library_books", listOf("library", "books", "study"), Icons.Filled.LibraryBooks),
    IconEntry("local_library", listOf("library", "read", "study"), Icons.Filled.LocalLibrary),
    IconEntry("cast_for_education", listOf("education", "learn", "class"), Icons.Filled.CastForEducation),
    IconEntry("science", listOf("science", "lab", "research"), Icons.Filled.Science),
    IconEntry("calculate", listOf("calculate", "math", "numbers"), Icons.Filled.Calculate),
    IconEntry("movie_filter", listOf("movie", "film", "popcorn"), Icons.Filled.MovieFilter),
    IconEntry("theaters", listOf("theater", "cinema", "movie"), Icons.Filled.Theaters),
    IconEntry("sports_esports", listOf("gaming", "esports", "game"), Icons.Filled.SportsEsports),
    IconEntry("sports_soccer", listOf("soccer", "football", "sport"), Icons.Filled.SportsSoccer),
    IconEntry("sports_basketball", listOf("basketball", "sport"), Icons.Filled.SportsBasketball),
    IconEntry("sports_tennis", listOf("tennis", "sport"), Icons.Filled.SportsTennis),
    IconEntry("sports_cricket", listOf("cricket", "sport"), Icons.Filled.SportsCricket),
    IconEntry("sports_golf", listOf("golf", "sport"), Icons.Filled.SportsGolf),
    IconEntry("casino", listOf("casino", "gambling", "dice"), Icons.Filled.Casino),
    IconEntry("music_note", listOf("music", "note", "song"), Icons.Filled.MusicNote),
    IconEntry("library_music", listOf("music", "library", "song"), Icons.Filled.LibraryMusic),
    IconEntry("videogame_asset", listOf("game", "video", "play"), Icons.Filled.VideogameAsset),
    IconEntry("smart_display", listOf("tv", "display", "stream"), Icons.Filled.SmartDisplay),
    IconEntry("live_tv", listOf("tv", "live", "watch"), Icons.Filled.LiveTv),
    IconEntry("tv", listOf("tv", "television", "watch"), Icons.Filled.Tv),
    IconEntry("headphones", listOf("headphones", "music", "audio"), Icons.Filled.Headphones),
    IconEntry("microphone", listOf("mic", "audio", "music"), Icons.Filled.Microphone),
    IconEntry("play_circle", listOf("play", "video", "media"), Icons.Filled.PlayCircle),
    IconEntry("hiking", listOf("hiking", "trek", "outdoor"), Icons.Filled.Hiking),
    IconEntry("kayaking", listOf("kayak", "water", "sport"), Icons.Filled.Kayaking),
    IconEntry("beach_access", listOf("beach", "vacation", "holiday"), Icons.Filled.BeachAccess),
    IconEntry("waves", listOf("waves", "sea", "beach"), Icons.Filled.Waves),
    IconEntry("terrain", listOf("terrain", "mountain", "nature"), Icons.Filled.Terrain),
    IconEntry("attractions", listOf("attraction", "theme", "park"), Icons.Filled.Attractions),
    IconEntry("museum", listOf("museum", "art", "culture"), Icons.Filled.Museum),
    IconEntry("photo_camera", listOf("camera", "photo", "picture"), Icons.Filled.PhotoCamera),
    IconEntry("landscape", listOf("landscape", "nature", "scenery"), Icons.Filled.Landscape),
    IconEntry("hotel", listOf("hotel", "stay", "travel"), Icons.Filled.Hotel),
    IconEntry("nightlife", listOf("night", "life", "party"), Icons.Filled.Nightlife),
    IconEntry("fitness_center", listOf("fitness", "gym", "workout"), Icons.Filled.FitnessCenter),
    IconEntry("directions_run", listOf("run", "running", "fitness"), Icons.Filled.DirectionsRun),
    IconEntry("self_improvement", listOf("meditation", "mind", "growth"), Icons.Filled.SelfImprovement),
    IconEntry("sports_gymnastics", listOf("gymnastics", "fitness", "sport"), Icons.Filled.SportsGymnastics),
    IconEntry("work", listOf("work", "job", "office"), Icons.Filled.Work),
    IconEntry("business", listOf("business", "company", "office"), Icons.Filled.Business),
    IconEntry("business_center", listOf("business", "briefcase", "work"), Icons.Filled.BusinessCenter),
    IconEntry("trending_down", listOf("trend", "decline", "decrease"), Icons.Filled.TrendingDown),
    IconEntry("show_chart", listOf("chart", "graph", "data"), Icons.Filled.ShowChart),
    IconEntry("pie_chart", listOf("pie", "chart", "data"), Icons.Filled.PieChart),
    IconEntry("insights", listOf("insights", "chart", "data"), Icons.Filled.Insights),
    IconEntry("monitoring", listOf("monitor", "chart", "stats"), Icons.Filled.Monitoring),
    IconEntry("donut_large", listOf("donut", "chart", "data"), Icons.Filled.DonutLarge),
    IconEntry("candlestick_chart", listOf("candlestick", "stock", "chart"), Icons.Filled.CandlestickChart),
    IconEntry("celebration", listOf("celebration", "party", "confetti"), Icons.Filled.Celebration),
    IconEntry("emoji_events", listOf("trophy", "event", "win"), Icons.Filled.EmojiEvents),
    IconEntry("star", listOf("star", "favorite", "rating"), Icons.Filled.Star),
    IconEntry("star_border", listOf("star", "outline", "rating"), Icons.Filled.StarBorder),
    IconEntry("volunteer_activism", listOf("volunteer", "donate", "charity"), Icons.Filled.VolunteerActivism),
    IconEntry("child_friendly", listOf("child", "baby", "kids"), Icons.Filled.ChildFriendly),
    IconEntry("toys", listOf("toys", "kids", "play"), Icons.Filled.Toys),
    IconEntry("auto_awesome", listOf("awesome", "sparkle", "special"), Icons.Filled.AutoAwesome),
    IconEntry("new_releases", listOf("new", "release", "announcement"), Icons.Filled.NewReleases),
    IconEntry("child_care", listOf("child", "baby", "kids"), Icons.Filled.ChildCare),
    IconEntry("family_restroom", listOf("family", "restroom"), Icons.Filled.FamilyRestroom),
    IconEntry("baby_changing_station", listOf("baby", "changing", "kids"), Icons.Filled.BabyChangingStation),
    IconEntry("label", listOf("label", "tag", "other"), Icons.Filled.Label),
    IconEntry("discount", listOf("discount", "percent", "sale"), Icons.Filled.Discount),
    IconEntry("percent", listOf("percent", "percentage", "discount"), Icons.Filled.Percent),
    IconEntry("check_circle", listOf("check", "done", "complete"), Icons.Filled.CheckCircle),
    IconEntry("done_all", listOf("done", "complete", "all"), Icons.Filled.DoneAll),
    IconEntry("verified", listOf("verified", "badge", "confirm"), Icons.Filled.Verified),
    IconEntry("extension", listOf("extension", "puzzle", "addon"), Icons.Filled.Extension),
    IconEntry("widgets", listOf("widgets", "apps", "grid"), Icons.Filled.Widgets),
    IconEntry("grid_view", listOf("grid", "view", "apps"), Icons.Filled.GridView),
    IconEntry("dashboard_customize", listOf("dashboard", "customize", "apps"), Icons.Filled.DashboardCustomize),
    IconEntry("tune", listOf("tune", "settings", "filter"), Icons.Filled.Tune),
    IconEntry("settings", listOf("settings", "gear", "preferences"), Icons.Filled.Settings),
    IconEntry("help", listOf("help", "question", "info"), Icons.Filled.Help),
    IconEntry("info", listOf("info", "information"), Icons.Filled.Info)
)

val CATEGORY_ICON_NAMES: List<String> = CATEGORY_ICONS.map { it.name }

fun materialIcon(name: String): ImageVector =
    CATEGORY_ICONS.find { it.name == name }?.imageVector ?: Icons.Filled.Category

fun searchIcons(query: String): List<IconEntry> {
    val q = query.trim().lowercase().replace("_", "").replace(" ", "")
    if (q.isEmpty()) return CATEGORY_ICONS
    return CATEGORY_ICONS.filter { entry ->
        (entry.name + entry.keywords.joinToString(" "))
            .lowercase()
            .replace("_", "").replace(" ", "")
            .contains(q)
    }
}

fun validateCategoryName(name: String, existing: List<Category>, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (trimmed.length > 30) return "Name must be 30 characters or fewer"
    val duplicate = existing.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) }
    if (duplicate) return "A category with this name already exists"
    return null
}
```

Note: the catalog above uses `Icons.Filled.RealEstateAgent`, `Icons.Filled.FamilyRestroom`, and `Icons.Filled.Hospital` — if any specific `Icons.Filled.*` name does not resolve at compile time, substitute the nearest existing `androidx.compose.material.icons.filled.*` name for that entry's vector (keep the entry's `name` and keywords unchanged) and note the substitution in your report. The `assembleDebug` build in Step 5 is the verification.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.CategoryIconsTest" -v`
Expected: PASS (11 tests).

- [ ] **Step 5: Verify the app still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/util/CategoryIcons.kt app/src/test/java/com/smsexpensetracker/ui/util/CategoryIconsTest.kt
git commit -m "feat(category): add searchable icon catalog"
```

---

### Task 2: Search field + grid in `CategoryDialog`

**Files:**
- Modify: `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt`

**Interfaces:**
- Consumes: `CATEGORY_ICONS` (as the default-icon source and search source), `searchIcons(query: String): List<IconEntry>` from Task 1.
- Produces: the final `CategoryDialog` with a search field + scrollable icon grid. Selection contract unchanged (`onSave(name, icon: String, color)`).

- [ ] **Step 1: Replace the icon picker**

In `app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt`:

1. Update imports — remove `import com.smsexpensetracker.ui.util.CATEGORY_ICON_NAMES`, add the following:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.IconButton
import com.smsexpensetracker.ui.util.CATEGORY_ICONS
import com.smsexpensetracker.ui.util.searchIcons
```

2. Change the default icon line (currently `var icon by remember { mutableStateOf(existing?.icon ?: CATEGORY_ICON_NAMES.first()) }`) to:

```kotlin
    var icon by remember { mutableStateOf(existing?.icon ?: CATEGORY_ICONS.first().name) }
    var iconQuery by remember { mutableStateOf("") }
```

3. Replace the entire `Column { Text("Icon", ...) ... }` icon block (currently the `FlowRow` over `CATEGORY_ICON_NAMES`) with:

```kotlin
                Column {
                    Text("Icon", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = iconQuery,
                        onValueChange = { iconQuery = it },
                        label = { Text("Search icons") },
                        singleLine = true,
                        trailingIcon = {
                            if (iconQuery.isNotEmpty()) {
                                IconButton(onClick = { iconQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(top = 8.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchIcons(iconQuery), key = { it.name }) { entry ->
                                val selected = entry.name == icon
                                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(
                                    imageVector = entry.imageVector,
                                    contentDescription = entry.name,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            icon = entry.name
                                            iconQuery = ""
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
```

The color `FlowRow` stays as-is (it uses `CATEGORY_COLORS`, untouched). The `@OptIn(ExperimentalLayoutApi::class)` annotation and `FlowRow` import remain — the color picker still uses `FlowRow`.

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the icon tests**

Run: `./gradlew testDebugUnitTest --tests "com.smsexpensetracker.ui.util.CategoryIconsTest" -v`
Expected: PASS (11 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsexpensetracker/ui/screens/categories/CategoryDialog.kt
git commit -m "feat(category): searchable icon picker in category dialog"
```

---

### Task 3: Full gate + docs

**Files:**
- Modify: `TESTING.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: everything from Tasks 1–2.

- [ ] **Step 1: Run the full gate**

Run: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleDebug`
Expected: ALL PASS, BUILD SUCCESSFUL. Baseline 354 + 11 (Task 1) = **365**. If your actual count differs, record the real number.

- [ ] **Step 2: Update TESTING.md**

Read the file first (it has numbered sections through §14 "Bulk Categorize" plus a summary table). Under the **Categories** section (§8), add one row for the searchable picker, matching the file's `- [ ] **Action** → Expected result` style:

```markdown
- [ ] **Add/Edit category** → icon picker now shows a "Search icons" field above a scrollable grid of ~120 icons. Typing "food" filters to the restaurant icon; tapping an icon selects it; selecting + Save persists the icon string.
```

Update the summary table: the test count (currently 354) → the real measured number; add a coverage note for `CategoryIconsTest` (`searchIcons`, `materialIcon`) in the Validation/UI-util row.

- [ ] **Step 3: Update TODO.md**

Read the file first. The bulk-categorize line lives under Task 11; the category-management items live under Task 14. Add one line matching the file's `- [x] **bold-lead**` style under Task 14 (Settings Screen → category management):

```markdown
  - [x] **Searchable icon picker** — category Add/Edit dialog has a "Search icons" field over a scrollable grid of ~120 curated Material icons (name + keyword search)
```

- [ ] **Step 4: Commit**

```bash
git add TESTING.md TODO.md
git commit -m "docs: add searchable icon picker to testing checklist and todo"
```

---

## Self-review notes

- **Spec coverage:** §4.1 catalog + `searchIcons`/`materialIcon` → Task 1; §4.2 search field + grid → Task 2; §7 acceptance criteria 5 (full gate) → Task 3. All 14 legacy keys included; default icon stays "restaurant" (`CATEGORY_ICONS.first()`).
- **Placeholder scan:** the catalog is fully written out; the only conditional is the explicit icon-name-substitution fallback in Task 1 Step 3 (a compile-time verification, not a placeholder).
- **Type consistency:** `IconEntry(name, keywords, imageVector)` defined in Task 1 and used identically in Task 2's `items(searchIcons(iconQuery), key = { it.name })`. `CATEGORY_ICON_NAMES` derived in Task 1 keeps `CategoryDialog` compiling until Task 2 removes it.
