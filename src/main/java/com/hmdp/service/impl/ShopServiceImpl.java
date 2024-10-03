package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public  Shop queryWithPassThrough(Long id){
        String shopInfo = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isNotBlank(shopInfo)){
            return JSONUtil.toBean(shopInfo, Shop.class);
        }
        if(shopInfo != null){
            return null;
        }
        Shop shop = getById(id);
        if(shop == null){
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "",2, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        return shop;
    }

    public  Shop queryWithMutex(Long id){
        String shopInfo = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isNotBlank(shopInfo)){
            return JSONUtil.toBean(shopInfo, Shop.class);
        }
        if(shopInfo != null){
            return null;
        }

        String lockey = "lock:shop:"+id;
        Shop shop = null;
        try {
            if(!tryLock(lockey)){
                Thread.sleep(60);
                return queryWithMutex(id);
            }

            shop = getById(id);
            if(shop == null){
                stringRedisTemplate.opsForValue().set("cache:shop:" + id, "",2, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockey);
        }
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);

    }
    @Override
    @Transactional
    public Result update(Shop shop) {

        updateById(shop);
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }
}
