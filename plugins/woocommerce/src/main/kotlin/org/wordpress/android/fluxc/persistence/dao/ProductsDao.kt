package org.wordpress.android.fluxc.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.wordpress.android.fluxc.persistence.entity.CouponAndProductEntity
import org.wordpress.android.fluxc.persistence.entity.ProductEntity

@Dao
abstract class ProductsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProduct(entity: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProducts(entities: List<ProductEntity>)

    @Query("SELECT * FROM Products p JOIN CouponsAndProducts c ON p.id = c.productId " +
        "WHERE c.isExcluded = :areExcluded AND c.couponId = :couponId ORDER BY p.id")
    abstract fun getCouponProducts(
        couponId: Long,
        areExcluded: Boolean
    ): List<ProductEntity>

    @Query("SELECT * FROM Products WHERE siteId = :siteId AND id IN (:productIds) ORDER BY id")
    abstract fun getProductsByIds(siteId: Long, productIds: List<Long>): List<ProductEntity>
}

