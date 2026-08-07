package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.CategoryDao
import com.smsexpensetracker.core.database.entity.CategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = db.categoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun categoryCrudRoundTrip() = runTest {
        val id = db.categoryDao().insert(CategoryEntity(name = "Food", icon = "", color = 0, isDefault = true))
        val found = db.categoryDao().getAllCategoryById(id)!!
        assertTrue(found.isDefault)

        db.categoryDao().update(found.copy(name = "Dining"))
        assertEquals("Dining", db.categoryDao().getAllCategoryById(id)!!.name)

        db.categoryDao().delete(db.categoryDao().getAllCategoryById(id)!!)
        assertNull(db.categoryDao().getAllCategoryById(id))
    }
}
