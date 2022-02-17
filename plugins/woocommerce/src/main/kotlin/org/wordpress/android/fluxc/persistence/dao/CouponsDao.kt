package org.wordpress.android.fluxc.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.wordpress.android.fluxc.persistence.entity.CouponAndProductCategoryEntity
import org.wordpress.android.fluxc.persistence.entity.CouponAndProductEntity
import org.wordpress.android.fluxc.persistence.entity.CouponEmailEntity
import org.wordpress.android.fluxc.persistence.entity.CouponEntity
import org.wordpress.android.fluxc.persistence.entity.CouponWithEmails

@Dao
abstract class CouponsDao {
    @Transaction
    @Query("SELECT * FROM Coupons WHERE siteId = :siteId ORDER BY id")
    abstract fun observeCoupons(siteId: Long): Flow<List<CouponWithEmails>>

    @Query("SELECT * FROM CouponsAndProductCategories WHERE isExcluded = :areExcluded")
    abstract fun getCouponProductCategories(
        areExcluded: Boolean
    ): List<CouponAndProductCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertCoupon(entity: CouponEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertCouponAndProductCategory(entity: CouponAndProductCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertCouponAndProduct(entity: CouponAndProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertCouponEmail(entity: CouponEmailEntity): Long

    @Transaction
    open suspend fun transaction(block: suspend () -> Unit) {
        block()
    }
}
