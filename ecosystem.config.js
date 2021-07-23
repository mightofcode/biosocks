module.exports = {
    apps: [
        {
            name: "biosocks",
            script: "java",
            args: " -jar ./target/biosocks-1.0.0.jar server",
            log_date_format: "YYYY-MM-DD HH:mm Z",
            env: {},
            env_production: {
            },
        }
    ],
};