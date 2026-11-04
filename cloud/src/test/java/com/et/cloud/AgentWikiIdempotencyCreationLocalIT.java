package com.et.cloud;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.model.entity.*;
import com.et.cloud.service.*;
import com.et.cloud.service.impl.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWikiIdempotencyCreationLocalIT {
    static String schema, password; static long userId, spaceId, folderId;
    static class ControlledJdbc extends JdbcTemplate {
        volatile boolean failCompletion;
        ControlledJdbc(DataSource ds){super(ds);}
        @Override public int update(String sql,Object... args){
            if(failCompletion && sql.startsWith("UPDATE agent_wiki_operation")) throw new IllegalStateException("injected before completion record");
            return super.update(sql,args);
        }
    }
    @Configuration @EnableTransactionManagement @MapperScan("com.et.cloud.mapper")
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource(url(),"awib_test",password);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean(); f.setDataSource(ds); com.baomidou.mybatisplus.core.MybatisConfiguration c=new com.baomidou.mybatisplus.core.MybatisConfiguration();c.setMapUnderscoreToCamelCase(false);c.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);f.setConfiguration(c);return f.getObject();}
        @Bean DataSourceTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean ControlledJdbc jdbc(DataSource ds){return new ControlledJdbc(ds);}
        @Bean UserService users(){UserService u=mock(UserService.class);User user=new User();user.setId(userId);user.setUserRole("user");user.setIsDelete(0);when(u.getById(anyLong())).thenReturn(user);return u;}
        @Bean WikiSpaceService spaces(){WikiSpaceService s=mock(WikiSpaceService.class);WikiSpace w=new WikiSpace();w.setId(spaceId);w.setOwnerUserId(userId);w.setType(0);w.setIsDelete(0);when(s.requireSpaceAccess(eq(spaceId),any(User.class),eq(true))).thenReturn(w);return s;}
        @Bean WikiFolderService folders(){WikiFolderService f=mock(WikiFolderService.class);WikiFolder d=new WikiFolder();d.setId(folderId);d.setSpaceId(spaceId);d.setName("test");d.setIsDelete(0);when(f.requireFolderAccess(eq(folderId),eq(spaceId),any(User.class),eq(true))).thenReturn(d);return f;}
        @Bean WikiCacheManager cache(){return mock(WikiCacheManager.class);}
        @Bean DocumentWikiServiceImpl documents(){return new DocumentWikiServiceImpl();}
        @Bean AgentWikiCreationService creation(UserService u,WikiSpaceService s,WikiSpaceMapper m,WikiFolderService f,DocumentWikiService d,WikiCacheManager c){return new AgentWikiCreationService(u,s,m,f,d,c);}
    }
    @Test void operationIdentityIsAtomicAcrossReplayConflictAndConcurrency() throws Exception {
        Method method = Arrays.stream(AgentWikiCreationService.class.getMethods()).filter(m -> m.getName().equals("createDocument") && m.getParameterCount()==6).findFirst().orElse(null);
        assertNotNull(method,"single-document operation identity path must exist");
        schema=req("AWIB_SCHEMA");password=req("AWIB_DB_PASSWORD");assertTrue(schema.matches("awib_[a-f0-9]{12}"));assertEquals("true",req("AWIB_INTERNAL_NETWORK_VERIFIED"));
        try(Connection c=DriverManager.getConnection(url(),"awib_test",password);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid,DATABASE(),(SELECT marker FROM baseline_owner_marker)")){assertTrue(r.next());assertEquals(req("AWIB_DB_UUID"),r.getString(1));assertEquals(schema,r.getString(2));assertEquals(req("AWIB_DB_MARKER"),r.getString(3));}
        JdbcTemplate raw=new JdbcTemplate(new DriverManagerDataSource(url(),"awib_test",password));String run="stage2-"+UUID.randomUUID();
        raw.update("INSERT INTO user(userAccount,userPassword,userRole,isDelete) VALUES(?,?,?,0)",run,"synthetic-only","user");userId=raw.queryForObject("SELECT id FROM user WHERE userAccount=?",Long.class,run);
        raw.update("INSERT INTO wiki_space(type,name,ownerUserId,isDelete) VALUES(0,?,?,0)",run,userId);spaceId=raw.queryForObject("SELECT id FROM wiki_space WHERE name=?",Long.class,run);
        raw.update("INSERT INTO wiki_folder(spaceId,name,isDelete) VALUES(?,?,0)",spaceId,run);folderId=raw.queryForObject("SELECT id FROM wiki_folder WHERE spaceId=? AND name=?",Long.class,spaceId,run);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        try(AnnotationConfigApplicationContext ctx=new AnnotationConfigApplicationContext(Config.class)){
            Object service=ctx.getBean(AgentWikiCreationService.class);String op=UUID.randomUUID().toString();
            StringBuilder evidence=new StringBuilder("scenario,group,documents,same_result\n");
            Object one=invoke(method,service,"same","synthetic content",op);Object two=invoke(method,service,"same","synthetic content",op);
            assertEquals(id(one),id(two));assertEquals(1L,docs(raw,"same"));
            evidence.append("sequential,0,1,true\n");
            assertThrows(InvocationTargetException.class,()->invoke(method,service,"same","changed content",op));assertEquals(1L,docs(raw,"same"));
            assertThrows(InvocationTargetException.class,()->method.invoke(service,userId,Long.toString(spaceId),Long.toString(folderId+1),"same","synthetic content",op));assertEquals(1L,docs(raw,"same"));
            UserService users=ctx.getBean(UserService.class);User active=new User();active.setId(userId);active.setUserRole("user");active.setIsDelete(0);
            when(users.getById(anyLong())).thenReturn(active,null);
            assertThrows(InvocationTargetException.class,()->invoke(method,service,"same","synthetic content",op));assertEquals(1L,docs(raw,"same"));
            doReturn(active).when(users).getById(anyLong());
            WikiSpaceService access=ctx.getBean(WikiSpaceService.class);
            when(access.requireSpaceAccess(eq(spaceId),any(User.class),eq(true))).thenThrow(new IllegalStateException("synthetic revoked permission"));
            assertThrows(InvocationTargetException.class,()->invoke(method,service,"same","synthetic content",op));assertEquals(1L,docs(raw,"same"));
            WikiSpace restored=new WikiSpace();restored.setId(spaceId);restored.setOwnerUserId(userId);restored.setType(0);restored.setIsDelete(0);
            doReturn(restored).when(access).requireSpaceAccess(eq(spaceId),any(User.class),eq(true));
            String beforeSave=UUID.randomUUID().toString();
            assertThrows(InvocationTargetException.class,()->invoke(method,service,"invalid"," ",beforeSave));
            assertEquals(0L,docs(raw,"invalid"));assertEquals(0L,operations(raw,beforeSave));
            String beforeRecord=UUID.randomUUID().toString();ControlledJdbc controlled=ctx.getBean(ControlledJdbc.class);
            controlled.failCompletion=true;
            try{assertThrows(InvocationTargetException.class,()->invoke(method,service,"rollback","synthetic content",beforeRecord));}
            finally{controlled.failCompletion=false;}
            assertEquals(0L,docs(raw,"rollback"));assertEquals(0L,operations(raw,beforeRecord));
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try{for(int i=0;i<30;i++){String title="parallel-"+i, operation=UUID.randomUUID().toString();CountDownLatch gate=new CountDownLatch(1);
                Future<Object>a=pool.submit(()->{gate.await();return invoke(method,service,title,"synthetic content",operation);});Future<Object>b=pool.submit(()->{gate.await();return invoke(method,service,title,"synthetic content",operation);});gate.countDown();assertEquals(id(a.get()),id(b.get()));assertEquals(1L,docs(raw,title));evidence.append("concurrent,").append(i).append(",1,true\n");}}
            finally{pool.shutdownNow();}
            invoke(method,service,"distinct","synthetic content",UUID.randomUUID().toString());invoke(method,service,"distinct","synthetic content",UUID.randomUUID().toString());assertEquals(2L,docs(raw,"distinct"));
            Method legacy=AgentWikiCreationService.class.getMethod("createDocument",long.class,String.class,String.class,String.class,String.class);
            legacy.invoke(service,userId,Long.toString(spaceId),Long.toString(folderId),"legacy","synthetic content");legacy.invoke(service,userId,Long.toString(spaceId),Long.toString(folderId),"legacy","synthetic content");assertEquals(2L,docs(raw,"legacy"));
            invoke(method,service,"optional-null","synthetic content",null);assertEquals(1L,docs(raw,"optional-null"));
            Path cwd=Path.of("").toAbsolutePath().normalize();Path root=Files.exists(cwd.resolve("deploy/schema-init.sql"))?cwd:cwd.getParent();
            Files.writeString(root.resolve("docs/issue-reports/agent-wiki-write-idempotency").resolve(run+"-creation.csv"),evidence.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
    }
    private static Object invoke(Method m,Object service,String title,String content,String op)throws Exception{return m.invoke(service,userId,Long.toString(spaceId),Long.toString(folderId),title,content,op);}
    private static String id(Object result)throws Exception{return (String)result.getClass().getField("documentId").get(result);}
    private static long docs(JdbcTemplate j,String title){return j.queryForObject("SELECT COUNT(*) FROM document_wiki WHERE spaceId=? AND (title=? OR title LIKE ?)",Long.class,spaceId,title,title+"（%）");}
    private static long operations(JdbcTemplate j,String op){return j.queryForObject("SELECT COUNT(*) FROM agent_wiki_operation WHERE user_id=? AND operation_id=?",Long.class,userId,op);}
    private static String url(){return "jdbc:mysql://127.0.0.1:3306/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";}
    private static String req(String n){String v=System.getenv(n);if(v==null||v.isBlank())throw new IllegalStateException("Missing "+n);return v;}
}
