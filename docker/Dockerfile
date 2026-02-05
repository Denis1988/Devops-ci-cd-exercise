FROM python:3.12-slim
RUN mkdir /opt/DevOps-CI-CD-exercise
COPY . /opt/DevOps-CI-CD-exercise

WORKDIR /opt/DevOps-CI-CD-exercise

RUN cd /opt/DevOps-CI-CD-exercise/
RUN pip3 install -r requirements.txt

CMD ["python3", "app.py"]