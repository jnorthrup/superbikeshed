FROM ubuntu:22.04

# Install basic tools and SSH server
RUN apt-get update && apt-get install -y \
    openssh-server \
    sudo \
    git \
    build-essential \
    cmake \
    libssl-dev \
    libudev-dev \
    pkg-config \
    libtool \
    automake \
    autoconf \
    libmnl-dev \
    libelf-dev \
    libcap-dev \
    libbfd-dev \
    libunwind-dev \
    libdw-dev \
    flex \
    bison \
    libz-dev \
    libaio-dev \
    libnuma-dev \
    libjson-c-dev \
    libcurl4-openssl-dev \
    libncurses5-dev \
    libncursesw5-dev \
    python3 \
    python3-pip \
    iproute2 \
    net-tools \
    vim \
    nano \
    wget \
    curl \
    gnupg \
    ca-certificates \
    lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user
ARG USERNAME=devuser
ARG USER_UID=1000
ARG USER_GID=1000
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Set up SSH
RUN mkdir /var/run/sshd
RUN echo 'PermitRootLogin yes' >> /etc/ssh/sshd_config
RUN echo 'PasswordAuthentication yes' >> /etc/ssh/sshd_config
RUN echo 'Subsystem sftp /usr/lib/openssh/sftp-server' >> /etc/ssh/sshd_config

# Expose SSH port
EXPOSE 22

# Install liburing
WORKDIR /opt
RUN git clone https://github.com/axboe/liburing.git
WORKDIR /opt/liburing
RUN ./configure --libdir=/usr/local/lib
RUN make && make install

# Install Go (for eBPF tools like bpftool)
RUN wget https://go.dev/dl/go1.22.4.linux-amd64.tar.gz
RUN tar -C /usr/local -xzf go1.22.4.linux-amd64.tar.gz
ENV PATH="/usr/local/go/bin:$PATH"

# Install bpftool (from kernel source, requires Go)
WORKDIR /opt
RUN git clone --depth 1 https://github.com/torvalds/linux.git
WORKDIR /opt/linux/tools/bpf/bpftool
RUN make
RUN cp bpftool /usr/local/bin/

# Set up entrypoint to start SSH and keep container running
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

# Switch to non-root user
USER $USERNAME
WORKDIR /home/$USERNAME
