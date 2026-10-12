# 后端运行镜像：只做「运行」，不做「构建」（构建在 GitHub Actions 里完成）。
# 服务器上执行 docker build 时只是 COPY 已构建好的 jar，不吃内存。
FROM eclipse-temurin:11-jre

# 与宿主机一致，避免 new Date() / DateUtil.formatDate 差 8 小时
ENV TZ=Asia/Shanghai
# 2G 内存机器的 JVM 上限：堆 512m + 元空间 160m，其余留给 MySQL/Redis/系统
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:MaxMetaspaceSize=160m -XX:+UseG1GC -Djava.awt.headless=true"

WORKDIR /app
COPY app.jar /app/app.jar

EXPOSE 8123

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
