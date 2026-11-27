# 4. Deploying microservices

This page goes with the fourth part of the Microservices lecture (slides 24 to 27). The
lecture shows four ways to deploy a service. This repo uses the third one, containers.

| Model | How | In this repo |
|---|---|---|
| 1. Language-specific package on servers | Build a jar, install a JDK on a few servers, copy the jar, run it under a service manager | You can: `java -jar menu-service/target/menu-service.jar` |
| 2. Virtual machine images | Bake one VM image per service (OS + JRE + jar), start as many VMs as needed | Not used |
| 3. **Containers** | Build one image per service, push it to a registry, run containers from it | **Used**: Dockerfiles, Docker Compose, images in GitHub Container Registry |
| 4. Serverless | Upload functions; the platform runs them on demand | Not used |

## 1. A jar on a server (slide 24)

Every service is already a self-contained jar with an embedded web server:

```bash
./mvnw -q package -DskipTests -pl menu-service
java -jar menu-service/target/menu-service.jar      # menu-service on port 8081, H2 database
```

On real servers you would install a JDK, copy the jar with the CI pipeline, and keep it
running with a service manager such as systemd. Several services can share one machine.
The caveat from the lecture: the service and the machine are tightly coupled. The
machine must have the right Java version, and two services on one machine compete for
its memory and ports.

## 2. Virtual machine images (slide 25)

A tool such as Packer would bake an image with the OS, a JRE and `menu-service.jar`,
and a cloud provider's auto-scaling group would start more VMs from it under load.
Each VM is well isolated, but it is big (gigabytes) and slow to start (minutes).

## 3. Containers (slide 26)

A container image holds the service and its runtime, but shares the host's OS kernel. It
is smaller than a VM image and starts in seconds. The lecture's three steps, for one
service:

```bash
# Step 1: package the service as a container image
./mvnw -q package -DskipTests -pl menu-service
docker build -t canteen/menu-service:1.0 menu-service

# Step 2: add the image to a registry (here GitHub Container Registry; Docker Hub works the same)
docker tag canteen/menu-service:1.0 ghcr.io/<your-github-name>/microservices-demo/menu-service:1.0
docker login ghcr.io -u <your-github-name>       # password: a token with write:packages
docker push ghcr.io/<your-github-name>/microservices-demo/menu-service:1.0

# Step 3: instantiate the service in a container (any machine with Docker can now do this)
docker run --rm -p 9081:8081 canteen/menu-service:1.0
curl http://localhost:9081/menu
```

The image is built from [menu-service/Dockerfile](../menu-service/Dockerfile): a Java
runtime image plus the jar, run as a non-root user. `docker compose up --build` does
step 1 and step 3 for all services at once.

### Step 2 in CI

You do not push images by hand. The `publish` job in
[.github/workflows/ci.yml](../.github/workflows/ci.yml) runs on every push to `main`
(never for pull requests), after the tests and the smoke test passed:

1. logs in to `ghcr.io` with the workflow's own `GITHUB_TOKEN` (the job has
   `permissions: packages: write`; no extra secret needed);
2. for each of the five services, runs `docker build`, then `docker tag`, then
   `docker push`, with two tags:
   - `ghcr.io/pradyunuydarp/microservices-demo/<service>:<commit sha>`: never changes,
     so you can always deploy exactly that version again;
   - `ghcr.io/pradyunuydarp/microservices-demo/<service>:latest`: the newest build of `main`.

New packages on GitHub are private at first. To let anyone pull them, open each package's
settings on GitHub and change its visibility to public. Then on any machine:

```bash
docker pull ghcr.io/pradyunuydarp/microservices-demo/menu-service:latest
docker run --rm -p 9081:8081 ghcr.io/pradyunuydarp/microservices-demo/menu-service:latest
```

Exercise: in a copy of `docker-compose.yml`, replace each `build: ./menu-service` with
`image: ghcr.io/pradyunuydarp/microservices-demo/menu-service:latest` (and so on). Now
`docker compose up` needs no Maven and no source code, only Docker and the registry.

### Container bloat, and why Kubernetes

The lecture's caveat is "container bloat". It has two meanings, and this repo shows both.

**Too many containers to manage.** One canteen already needs 8 containers. Add a few
more services, two replicas of each for safety, and several machines, and Compose on one
host is no longer enough. Somebody has to decide which machine runs which container,
restart crashed ones, spread load over replicas, roll out a new version without downtime,
and hand out secrets. That is a container orchestrator's job, usually **Kubernetes**.
The ideas map closely:

| Docker Compose here | Kubernetes |
|---|---|
| one entry under `services:` | a Deployment (how many replicas, which image) plus a Service (a stable DNS name and load balancing) |
| `healthcheck:` with `/actuator/health` | liveness and readiness probes on the same endpoint |
| `depends_on` | none: every service must cope with others being down (order-service already does) |
| `environment:` | a ConfigMap; the client secrets go into a Secret |
| published port 8080 | an Ingress, with TLS |
| `docker compose up --scale order-service=2` | `kubectl scale deployment order-service --replicas=2`, or an autoscaler |

**Images that are too big.** Each image here is about 350 to 415 MB, and about 300 MB of
that is the Java runtime image they are all built on. Docker stores that base layer only
once per machine, which helps. To shrink further: a runtime built with `jlink` that holds
only the Java modules the service uses, a distroless base image, or a native image with
GraalVM. Smaller images pull faster, start faster and contain fewer packages that can
have vulnerabilities.

## 4. Serverless (slide 27)

"Serverless" means you do not manage servers, not that there are none. You implement
functions with an entry point (a handler), upload them, and the platform (AWS Lambda, for
example) runs them when a request or an event arrives, and bills per call.

This repo is not serverless. To move, say, the batch menu lookup there, you would write
it as a function (Spring Cloud Function can turn a `Function<List<Long>, List<MenuItem>>`
into a Lambda handler), upload it, and route `GET /menu` to it. Things to watch: a JVM
starts slowly ("cold start"; GraalVM native images or AWS SnapStart help), functions keep
no state between calls, and every call opening a database connection can overwhelm the
database.

## Where this repo sits

Containers on one machine with Docker Compose, images built and pushed to a registry by
CI. The natural next step is to run the same images on Kubernetes.
