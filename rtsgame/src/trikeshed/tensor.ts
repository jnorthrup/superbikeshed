import { Tensor, Series, Join } from './types';
import { j } from './join';
import { size, getSeriesValue } from './series';

/** Returns the shape of the Tensor. */
export function tensorShape<T>(tensor: Tensor<T>): number[] {
    return tensor.shape;
}

/** Returns the accessor function of the Tensor. */
export function tensorAccessor<T>(tensor: Tensor<T>): (coords: number[]) => T {
    return (coords: number[]) => getTensorValue(tensor, coords);
}

// Syntactic sugar
export const shape = tensorShape;
export const accessor = tensorAccessor;

/** Returns the rank (number of dimensions) of the Tensor. */
export function tensorRank<T>(tensor: Tensor<T>): number {
    return tensorShape(tensor).length;
}
export const rank = tensorRank;

/** Returns the total number of elements in the Tensor. */
export function tensorTotalSize<T>(tensor: Tensor<T>): number {
    const s = tensorShape(tensor);
    if (s.length === 0) return 0;
    return s.reduce((a, b) => a * b, 1);
}

export function TensorConstruct<T>(shape: number[], accessor: (coords: number[]) => T): Tensor<T> {
    const totalSize = shape.reduce((a, b) => a * b, 1);
    const data: T[] = new Array(totalSize);
    const stride = calculateStride(shape);
    
    for (let i = 0; i < totalSize; i++) {
        data[i] = accessor(linearToCoords({ shape, stride, data }, i));
    }
    
    return { shape, stride, data };
}

export function TensorSeries<T>(size: number, accessor: (index: number) => T): Tensor<T> {
    return TensorConstruct([size], ([i]) => accessor(i));
}

export function TensorCursor<T>(rows: number, cols: number, accessor: (row: number, col: number) => T): Tensor<T> {
    return TensorConstruct([rows, cols], ([i, j]) => accessor(i, j));
}

export function getTensorValue<T>(tensor: Tensor<T>, coords: number[]): T {
    const index = coordsToLinear(tensor, coords);
    return tensor.data[index];
}

export function getTensorValueVarArgs<T>(tensor: Tensor<T>, ...coords: number[]): T {
    return getTensorValue(tensor, coords);
}

export function getTensorValueRank1<T>(tensor: Tensor<T>, i: number): T {
    if (tensorRank(tensor) !== 1) {
        throw new Error("Tensor must be rank 1");
    }
    return getTensorValue(tensor, [i]);
}

export function getTensorValueRank2<T>(tensor: Tensor<T>, i: number, j: number): T {
    if (tensorRank(tensor) !== 2) {
        throw new Error("Tensor must be rank 2");
    }
    return getTensorValue(tensor, [i, j]);
}

export function alphaConvert<X, C>(tensor: Tensor<X>, transform: (value: X) => C): Tensor<C> {
    return {
        ...tensor,
        data: tensor.data.map(transform)
    };
}

export function linearToCoords(tensor: Tensor<any>, linearIndex: number): number[] {
    const coords: number[] = new Array(tensorRank(tensor));
    let remaining = linearIndex;
    
    for (let i = 0; i < tensorRank(tensor); i++) {
        coords[i] = Math.floor(remaining / tensor.stride[i]);
        remaining %= tensor.stride[i];
    }
    
    return coords;
}

export function coordsToLinear(tensor: Tensor<any>, coords: number[]): number {
    if (coords.length !== tensorRank(tensor)) {
        throw new Error("Coordinate dimensions must match tensor rank");
    }
    
    let index = 0;
    for (let i = 0; i < coords.length; i++) {
        if (coords[i] < 0 || coords[i] >= tensor.shape[i]) {
            throw new RangeError(`Coordinate ${i} out of bounds`);
        }
        index += coords[i] * tensor.stride[i];
    }
    
    return index;
}

export function materialize<T>(tensor: Tensor<T>): T[] {
    return [...tensor.data];
}

function calculateStride(shape: number[]): number[] {
    const stride = new Array(shape.length);
    let currentStride = 1;
    
    for (let i = shape.length - 1; i >= 0; i--) {
        stride[i] = currentStride;
        currentStride *= shape[i];
    }
    
    return stride;
}

export function broadcastShapes(shape1: number[], shape2: number[]): number[] {
    const rank1 = shape1.length;
    const rank2 = shape2.length;
    const maxRank = Math.max(rank1, rank2);
    const result = new Array(maxRank);
    
    for (let i = 0; i < maxRank; i++) {
        const dim1 = i < rank1 ? shape1[rank1 - 1 - i] : 1;
        const dim2 = i < rank2 ? shape2[rank2 - 1 - i] : 1;
        
        if (dim1 !== 1 && dim2 !== 1 && dim1 !== dim2) {
            throw new Error("Shapes are not broadcastable");
        }
        
        result[maxRank - 1 - i] = Math.max(dim1, dim2);
    }
    
    return result;
}

export function calculateBroadcastCoordinateMap(sourceShape: number[], targetShape: number[]): number[] {
    const sourceRank = sourceShape.length;
    const targetRank = targetShape.length;
    const maxRank = Math.max(sourceRank, targetRank);
    const result = new Array(maxRank);
    
    for (let i = 0; i < maxRank; i++) {
        const sourceDim = i < sourceRank ? sourceShape[sourceRank - 1 - i] : 1;
        const targetDim = i < targetRank ? targetShape[targetRank - 1 - i] : 1;
        
        if (sourceDim === 1) {
            result[maxRank - 1 - i] = 0;
        } else if (sourceDim === targetDim) {
            result[maxRank - 1 - i] = i;
        } else {
            throw new Error("Shapes are not broadcastable");
        }
    }
    
    return result;
}

export function zip<A, B>(tensorA: Tensor<A>, tensorB: Tensor<B>): Tensor<Join<A, B>> {
    if (tensorRank(tensorA) !== tensorRank(tensorB)) {
        throw new Error("Tensors must have the same rank");
    }
    
    const shape = tensorShape(tensorA);
    for (let i = 0; i < shape.length; i++) {
        if (shape[i] !== tensorShape(tensorB)[i]) {
            throw new Error("Tensors must have the same shape");
        }
    }
    
    return {
        ...tensorA,
        data: tensorA.data.map((a, i) => j(a, tensorB.data[i]))
    };
}

export function combine<A, B, C>(
    tensorA: Tensor<A>,
    tensorB: Tensor<B>,
    transform: (valA: A, valB: B) => C
): Tensor<C> {
    if (tensorRank(tensorA) !== tensorRank(tensorB)) {
        throw new Error("Tensors must have the same rank");
    }
    
    const shape = tensorShape(tensorA);
    for (let i = 0; i < shape.length; i++) {
        if (shape[i] !== tensorShape(tensorB)[i]) {
            throw new Error("Tensors must have the same shape");
        }
    }
    
    return {
        ...tensorA,
        data: tensorA.data.map((a, i) => transform(a, tensorB.data[i]))
    };
} 