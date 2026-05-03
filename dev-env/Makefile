IMAGE  ?= localhost/rts-dev-toolbox
TAG    ?= latest
NAME   ?= rts-dev

build:
	podman build -t $(IMAGE):$(TAG) -f Containerfile .

create:
	toolbox create --image $(IMAGE):$(TAG) $(NAME)

enter:
	toolbox enter $(NAME)

stop:
	-toolbox run -c $(NAME) emacsclient --eval '(kill-emacs)' 2>/dev/null
	-podman stop $(NAME)

rm: stop
	toolbox rm $(NAME)

.PHONY: build create enter stop rm
