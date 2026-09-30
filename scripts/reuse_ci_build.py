#!/usr/bin/env python3
"""Reuse completed APK builds when only the CI harness changed."""
import base64
import json
import os
import subprocess

import yaml


HARNESS_FILES = {
    ".github/workflows/android.yml",
    "scripts/crash_crawl.py",
    "scripts/test_crash_crawl.py",
    "scripts/reuse_ci_build.py",
    "scripts/test_reuse_ci_build.py",
    "scripts/promote_preview_apks.py",
    "scripts/test_promote_preview_apks.py",
}


def same_build_inputs(previous, current):
    def inputs(workflow):
        build = workflow["jobs"]["build"]
        steps = [s for s in build["steps"]
                 if s.get("name") != "Test crash-hunt readiness checks"]
        return workflow.get("env"), build.get("env"), build["runs-on"], steps
    return inputs(previous) == inputs(current)


def reusable(compare, jobs, artifacts, previous, current):
    return (
        compare.get("status") in ("ahead", "identical")
        and all(f["filename"] in HARNESS_FILES for f in compare.get("files", []))
        and any(j["name"] == "build" and j["conclusion"] == "success" for j in jobs)
        and {"Ghajarvpn-Android-debug", "Ghajarvpn-Android-preview"}.issubset(
            {a["name"] for a in artifacts if not a["expired"]})
        and same_build_inputs(previous, current)
    )


def crash_certified(run, jobs, source_tree, current_tree):
    passed = {j["name"] for j in jobs if j["conclusion"] == "success"}
    return (run.get("conclusion") == "success" and source_tree == current_tree
            and {"prepare", "crash-hunt"}.issubset(passed))


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    head = os.environ["GITHUB_SHA"]
    own_run = os.environ["GITHUB_RUN_ID"]
    requested = os.environ.get("REUSE_BUILD_RUN", "").strip()
    if requested and not requested.isdigit():
        raise ValueError("reuse-build-run must be a numeric GitHub Actions run ID")

    def api(path):
        result = subprocess.run(["gh", "api", f"repos/{repo}/{path}"],
                                check=True, capture_output=True, text=True)
        return json.loads(result.stdout)

    with open(".github/workflows/android.yml") as f:
        current = yaml.safe_load(f)
    runs = api("actions/workflows/android.yml/runs?per_page=30")["workflow_runs"]
    candidates = [api(f"actions/runs/{requested}")] if requested else runs
    selected = ""
    for run in candidates:
        if str(run["id"]) == own_run:
            continue
        try:
            sha = run["head_sha"]
            compare = api(f"compare/{sha}...{head}")
            if (compare.get("status") not in ("ahead", "identical")
                    or any(f["filename"] not in HARNESS_FILES for f in compare.get("files", []))):
                continue
            old = api(f"contents/.github/workflows/android.yml?ref={sha}")
            previous = yaml.safe_load(base64.b64decode(old["content"]))
            if not same_build_inputs(previous, current):
                continue
            jobs = api(f"actions/runs/{run['id']}/jobs?filter=all&per_page=100")["jobs"]
            artifacts = api(f"actions/runs/{run['id']}/artifacts?per_page=100")["artifacts"]
            if reusable(compare, jobs, artifacts, previous, current):
                selected = str(run["id"])
                print(f"Reusing successful build from {run['html_url']} ({sha}); APK build inputs are unchanged.")
                break
        except (subprocess.CalledProcessError, KeyError, yaml.YAMLError) as e:
            if requested:
                raise RuntimeError("Could not validate the requested APK build") from e
            print(f"Skipping unavailable build run {run['id']}.")
    if requested and not selected:
        raise ValueError("Requested run has no reusable successful build with matching APK inputs")
    if not selected:
        print("APK inputs changed or no completed APK build exists; a build is required.")
    certified = ""
    current_tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], text=True).strip()
    for run in runs:
        if str(run["id"]) == own_run or run.get("conclusion") != "success":
            continue
        source_tree = api(f"git/commits/{run['head_sha']}")["tree"]["sha"]
        if source_tree != current_tree:
            continue
        jobs = api(f"actions/runs/{run['id']}/jobs?per_page=100")["jobs"]
        if crash_certified(run, jobs, source_tree, current_tree):
            certified = str(run["id"])
            print(f"Crash-hunt already passed for this exact source tree: {run['html_url']}")
            break
    with open(os.environ["GITHUB_OUTPUT"], "a") as f:
        f.write(f"reuse-run-id={selected}\n")
        f.write(f"reuse-crash-run-id={certified}\n")


if __name__ == "__main__":
    main()
