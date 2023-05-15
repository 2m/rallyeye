import { spawnSync } from "child_process";
import { defineConfig } from "vite";
import { readFileSync } from "fs";

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
        ? spawnSync("sbt.bat", args.map(x => `"${x}"`), { shell: true, cwd: "../../", ...options })
        : spawnSync("sbt", args, { cwd: "../../", ...options });

    if (result.error)
        throw result.error;
    if (result.status !== 0)
        throw new Error(`sbt process failed with exit code ${result.status}`);
    const linkerDir = readFileSync("target/linker-output.txt")
    console.log(`"${task}" task output: [${linkerDir}]`)
    return linkerDir;
}

const linkOutputDir = isDev()
    ? printSbtTask("frontend/publicDev")
    : printSbtTask("frontend/publicProd");

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
