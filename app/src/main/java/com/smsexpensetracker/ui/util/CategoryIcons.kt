package com.smsexpensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.smsexpensetracker.domain.model.Category

val CATEGORY_COLORS = listOf(
    -13108, -13956304, -48060, -13676760, -10496, -16581634,
    -14513374, -12664161, -4880347, -7084816, -7829368, -10980385
)

val CATEGORY_ICON_NAMES = listOf(
    "restaurant", "shopping_cart", "local_gas_station", "receipt",
    "shopping_bag", "movie", "local_hospital", "directions_car",
    "school", "home", "flight", "payments", "trending_up", "category"
)

fun materialIcon(name: String): ImageVector = when (name) {
    "restaurant" -> Icons.Filled.Restaurant
    "shopping_cart" -> Icons.Filled.ShoppingCart
    "local_gas_station" -> Icons.Filled.LocalGasStation
    "receipt" -> Icons.Filled.Receipt
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "movie" -> Icons.Filled.Movie
    "local_hospital" -> Icons.Filled.LocalHospital
    "directions_car" -> Icons.Filled.DirectionsCar
    "school" -> Icons.Filled.School
    "home" -> Icons.Filled.Home
    "flight" -> Icons.Filled.Flight
    "payments" -> Icons.Filled.Payments
    "trending_up" -> Icons.Filled.TrendingUp
    "category" -> Icons.Filled.Category
    else -> Icons.Filled.Category
}

fun validateCategoryName(name: String, existing: List<Category>, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Name is required"
    if (trimmed.length > 30) return "Name must be 30 characters or fewer"
    val duplicate = existing.any { it.id != editingId && it.name.equals(trimmed, ignoreCase = true) }
    if (duplicate) return "A category with this name already exists"
    return null
}
