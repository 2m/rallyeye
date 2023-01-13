import { spawnSync } from "child_process";
import { defineConfig } from "vite";

function isDev() {
    return process.env.NODE_ENV !== "production";
}

function printSbtTask(task) {
    const args = ["--client", "--error", "--batch", `print ${task}`];
    const options = {
        stdio: [
            "pipe", // StdIn.
            "pipe", // StdOut.
            "inherit", // StdErr.
        ],
    };
    const result = process.platform === 'win32'
        ? spawnSync("sbt.bat", args.map(x => `"${x}"`), { shell: true, ...options })
        : spawnSync("sbt", args, options);

    if (result.error)
        throw result.error;
    if (result.status !== 0)
        throw new Error(`sbt process failed with exit code ${result.status}`);
    const value = result.stdout.toString('utf8').replace(/[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]/g,"").trim().split('\n').at(-2);
    console.log(`"${task}" task output: [${value}]`)
    return value;
}

const linkOutputDir = isDev()
    ? printSbtTask("publicDev")
    : printSbtTask("publicProd");

export default defineConfig({
    resolve: {
        alias: [
            {
                find: "@linkOutputDir",
                replacement: linkOutputDir,
            },
        ],
    },
    server: {
        host: "0.0.0.0"
    }
});
