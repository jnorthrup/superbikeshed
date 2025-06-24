

import { instantiate } from './Trikeshed-Monorepo-Trikeshed-wasm-js.uninstantiated.mjs';


const exports = (await instantiate({

})).exports;

export const {
memory,
_initialize
} = exports


