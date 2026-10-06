package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletMapper {
    Map<String,Object> balances(@Param("userId") Object userId);
    List<Map<String,Object>> entries(@Param("userId") Object userId, @Param("offset") int offset);
    Long entryCount(@Param("userId") Object userId);
    List<Map<String,Object>> withdrawals(@Param("userId") Object userId);
    List<Map<String,Object>> paymentApplication(@Param("id") Object id);
    Map<String,Object> lockJob(@Param("id") Object id);
    Map<String,Object> lockApplication(@Param("id") Object id);
    Integer paymentCount(@Param("applicationId") Object applicationId);
    int insertPayment(@Param("id") Object id, @Param("applicationId") Object applicationId, @Param("publisherId") Object publisherId, @Param("studentId") Object studentId, @Param("amountCents") Object amountCents);
    int insertWithdrawal(@Param("id") Object id, @Param("userId") Object userId, @Param("amountCents") Object amountCents);
    List<Map<String,Object>> lockWithdrawal(@Param("id") Object id, @Param("userId") Object userId);
    int cancelWithdrawal(@Param("id") Object id);
    Map<String,Object> lockWallet(@Param("userId") Object userId);
    int updateBalances(@Param("balanceCents") Object balanceCents, @Param("frozenCents") Object frozenCents, @Param("userId") Object userId);
    int insertEntry(@Param("id") Object id, @Param("userId") Object userId, @Param("operationId") Object operationId, @Param("kind") Object kind, @Param("deltaCents") Object deltaCents, @Param("frozenDeltaCents") Object frozenDeltaCents, @Param("balanceCents") Object balanceCents, @Param("frozenCents") Object frozenCents, @Param("description") Object description);
    List<Map<String,Object>> operation(@Param("actorId") Object actorId, @Param("requestKey") Object requestKey);
    int insertOperation(@Param("id") Object id, @Param("actorId") Object actorId, @Param("requestKey") Object requestKey, @Param("kind") Object kind, @Param("targetId") Object targetId, @Param("amountCents") Object amountCents);
}
