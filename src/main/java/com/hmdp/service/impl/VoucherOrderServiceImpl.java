package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisWorker redisWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //单服务器下解决一人一单问题
//        synchronized (userId.toString().intern()){
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        }
        //利用分布式锁多服务器下解决一人一单
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean success = lock.tryLock(5);
        if(!success){
            return Result.fail("抢购失败");

        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }

    }
    @Transactional
     public Result creatVoucherOrder(Long voucherId){
         Long userId = UserHolder.getUser().getId();

         int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
         if(count > 0){
             return Result.fail("用户已经购买过一次");
         }
         boolean success = seckillVoucherService.update()
                 .setSql("stock = stock - 1")
                 .eq("voucher_id", voucherId)
                 .gt("status", 0)
                 .update();
         if (!success){
             return Result.fail("库存不足");
         }

         VoucherOrder voucherOrder = new VoucherOrder();
         long orderId = redisWorker.nextId("order");
         voucherOrder.setId(orderId);

         voucherOrder.setUserId(userId);
         voucherOrder.setVoucherId(voucherId);

         save(voucherOrder);
         return Result.ok(orderId);
     }
}
