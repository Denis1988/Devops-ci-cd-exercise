FROM python:3.13.12-slim

# Set the working directory first
WORKDIR /opt/DevOps-CI-CD-exercise

# Copy everything into the working directory
COPY . .

RUN groupmod -g 130 docker || groupadd -g 130 docker
RUN usermod -aG docker jenkins
USER jenkins

# Install dependencies
RUN pip3 install --no-cache-dir -r requirements.txt