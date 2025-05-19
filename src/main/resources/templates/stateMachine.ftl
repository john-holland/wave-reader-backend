<#-- State Machine Component Template -->
<div class="state-machine" data-id="${machine.id}" data-version="${version}">
    <div class="state-machine-header">
        <h3>State Machine: ${machine.id}</h3>
        <span class="version">v${version}</span>
    </div>
    
    <div class="state-machine-body">
        <div class="current-state">
            <h4>Current State</h4>
            <div class="state-badge">${currentState}</div>
        </div>
        
        <div class="context">
            <h4>Context</h4>
            <pre>${machine.context}</pre>
        </div>
        
        <div class="transitions">
            <h4>Available Transitions</h4>
            <div class="transition-buttons">
                <#list availableTransitions as transition>
                    <button 
                        class="transition-button"
                        data-event="${transition}"
                        onclick="transitionState('${machine.id}', '${transition}')">
                        ${transition}
                    </button>
                </#list>
            </div>
        </div>
    </div>
</div>

<style>
.state-machine {
    border: 1px solid #ddd;
    border-radius: 4px;
    padding: 1rem;
    margin: 1rem 0;
}

.state-machine-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
}

.version {
    font-size: 0.8rem;
    color: #666;
}

.state-badge {
    display: inline-block;
    padding: 0.25rem 0.5rem;
    background-color: #e3f2fd;
    border-radius: 4px;
    font-weight: bold;
}

.transition-buttons {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
}

.transition-button {
    padding: 0.5rem 1rem;
    border: 1px solid #2196f3;
    background-color: white;
    color: #2196f3;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.2s;
}

.transition-button:hover {
    background-color: #2196f3;
    color: white;
}
</style>

<script>
function transitionState(machineId, event) {
    const mutation = `
        mutation TransitionState($id: ID!, $event: String!) {
            transitionStateMachine(id: $id, event: $event) {
                id
                currentState
                context
                version
                availableTransitions
            }
        }
    `;

    fetch('/graphql', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            query: mutation,
            variables: {
                id: machineId,
                event: event
            }
        })
    })
    .then(response => response.json())
    .then(data => {
        // Update the component with new state
        const machine = data.data.transitionStateMachine;
        const component = document.querySelector(`[data-id="${machineId}"]`);
        if (component) {
            component.outerHTML = renderStateMachine(machine);
        }
    });
}

// Subscribe to state machine updates
function subscribeToUpdates(machineId) {
    const subscription = `
        subscription StateMachineUpdated($id: ID!) {
            stateMachineUpdated(id: $id) {
                id
                currentState
                context
                version
                availableTransitions
            }
        }
    `;

    // Implement WebSocket subscription here
    // This is a placeholder for the actual WebSocket implementation
    const ws = new WebSocket('ws://localhost:8080/graphql');
    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.data?.stateMachineUpdated) {
            const machine = data.data.stateMachineUpdated;
            const component = document.querySelector(`[data-id="${machineId}"]`);
            if (component) {
                component.outerHTML = renderStateMachine(machine);
            }
        }
    };
}
</script> 