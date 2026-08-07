package com.smsexpensetracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsexpensetracker.core.database.dao.UserCategoryRuleDao
import com.smsexpensetracker.core.database.entity.CategoryEntity
import com.smsexpensetracker.core.database.entity.UserCategoryRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCategoryRuleDaoTest {

    private lateinit var db: SmsExpenseDatabase
    private lateinit var userCategoryRuleDao: UserCategoryRuleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmsExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userCategoryRuleDao = db.userCategoryRuleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ruleCrudAndCascadeDelete() = runTest {
        val catId = db.categoryDao().insert(CategoryEntity(name = "Shopping", icon = "", color = 0))
        val rule = UserCategoryRuleEntity(pattern = "amazon", categoryId = catId)
        val id = db.userCategoryRuleDao().insert(rule)

        assertEquals(1, db.userCategoryRuleDao().getAll().first().size)
        assertEquals("amazon", db.userCategoryRuleDao().getAll().first()[0].pattern)

        db.userCategoryRuleDao().deleteByCategory(catId)
        assertEquals(0, db.userCategoryRuleDao().getAll().first().size)

        db.userCategoryRuleDao().insert(rule.copy(id = 0L))
        db.categoryDao().delete(db.categoryDao().getAllCategoryById(catId)!!)
        assertEquals(0, db.userCategoryRuleDao().getAll().first().size)
    }
}
