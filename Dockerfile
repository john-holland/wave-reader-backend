FROM gradle:8.5-jdk17

WORKDIR /app

COPY . .

EXPOSE 3000

CMD ["gradle", "run", "--continuous"]
