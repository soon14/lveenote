package com.keduw.aspect;

import com.keduw.annotation.Log;
import com.keduw.model.SysLog;
import com.keduw.util.HttpContextUtils;
import com.keduw.util.IPUtils;
import com.keduw.util.JsonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.*;

/**

 * @author hongshengfeng
 * @date 2020/07/07
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    private static ExecutorService executor = new ThreadPoolExecutor(1, 5, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2000), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("sys-log-thread");
            return thread;
        }
    });

    @Pointcut("@annotation(com.keduw.annotation.Log)")
    public void logPointCut(){}

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result = point.proceed();
        long endTime = System.currentTimeMillis();
        //保存日志
        executor.submit(() -> saveSysLog(point, endTime - beginTime));
        return result;
    }

    private void saveSysLog(ProceedingJoinPoint joinPoint, long time){
        SysLog sysLog = new SysLog();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //注解上的描述
        Log log = method.getAnnotation(Log.class);
        if(log != null){
            sysLog.setOperation(log.value());
        }
        //请求的方法名
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName + "()");
        //请求的参数
        Object[] args = joinPoint.getArgs();
        sysLog.setParams(JsonUtils.objectToJson(args[0]));
        //获取request
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        //设置IP地址
        sysLog.setIp(IPUtils.getIpAddr(request));
        //用户名
        //String username = ((SysUserEntity) SecurityUtils.getSubject().getPrincipal()).getUsername();
        //sysLog.setUsername(username);
        sysLog.setTime(time);
        sysLog.setCreateDate(new Date());
        logger.info("record --> {}", sysLog);
    }
}
